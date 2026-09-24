package g_mungus.zps.tractor;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;


/**
 * What a Tractor Beam finds down each of its columns. Runs identically on both sides, so the client can draw each
 * column to the right length and predict what gets pulled without the server sending any of it.
 * <p>
 * Two separate things come out of a column. Its <em>reach</em> is how far the beam gets before something solid
 * stops it, which is what shields entities and particles behind a wall. Its <em>target</em> is the nearest block
 * of any kind, which is the only one the beam may pull loose.
 */
public final class BeamScan {

    /** Blocks the beam never pulls loose. Empty by default; they stop the column like any immovable block. */
    public static final TagKey<Block> IMMUNE_BLOCKS =
            TagKey.create(Registries.BLOCK, ZPSMod.resource("tractor_beam_immune"));

    public enum ColumnHit {
        /** Nothing but air and liquid as far as the beam reaches. */
        CLEAR,
        /** A piston could push it: it comes loose as a falling block. */
        PULLABLE,
        /** A piston would break it: it breaks, and its drops are pulled instead. */
        DESTROYABLE,
        /** A piston could not move it: the column ends here. */
        BLOCKED
    }

    private final int[] reach;
    private final ColumnHit[] hits;
    private final BlockPos[] targets;

    private BeamScan(int columns) {
        reach = new int[columns];
        hits = new ColumnHit[columns];
        targets = new BlockPos[columns];
    }

    /**
     * Where across a column, from its centre line, rays are cast down it. One down the middle finds what a column
     * is aimed at; the four toward its corners find what a grid lying at an angle pokes into it from the side.
     */
    private static final double[][] RAYS = {{0, 0}, {-0.3, -0.3}, {-0.3, 0.3}, {0.3, -0.3}, {0.3, 0.3}};
    /** Rays start this far out from the mouth, so as not to start inside the panel. */
    private static final double RAY_START = 0.05;
    /**
     * For tests: cast rays even with no other grid about. Neither ship mod runs in the development environment,
     * and in a plain world the rays can only find what the walk already found, so with this set a scan must come
     * out exactly as it does without. That is a check on the rays themselves: that they do not catch the panel,
     * and that what they touch is put at the right distance.
     */
    @VisibleForTesting
    public static boolean castEverywhere;

    /**
     * Looks down every column.
     * <p>
     * In the panel's own grid a column is an exact line of blocks, and is walked as one. With a ship mod present
     * the beam may cross other grids as well: the world, if the panel rides a ship, and any ship that reaches into
     * the beam. Those lie at some angle to the column and there is no line of blocks to walk. They are found by
     * casting rays down the column with the level's own {@code clip}, which both Valkyrien Skies and Sable extend
     * to take in every grid a ray passes through. What the rays find nearer than the walk did takes its place.
     * <p>
     * A block a ray finds in another grid comes back as an ordinary position in this level, since both ship mods
     * keep their blocks in it, so everything downstream of the scan reads and removes it the usual way.
     */
    public static BeamScan scan(Level level, BeamGeometry beam) {
        BeamScan result = new BeamScan(beam.columns());
        Direction pull = beam.facing().getOpposite();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Rays are only worth casting where there is another grid for them to find: when the panel is on one,
        // which makes the world another, or when one reaches into the beam.
        boolean onGrid = Compat.gridOf(level, beam.controller()) != null;
        boolean crossesGrids = castEverywhere || onGrid
                || !Compat.gridsTouching(level, beam.worldBounds(level)).isEmpty();

        for (int column = 0; column < beam.columns(); column++) {
            result.reach[column] = beam.range();
            result.hits[column] = ColumnHit.CLEAR;
            BlockPos base = beam.columnBase(column);
            int targetDistance = Integer.MAX_VALUE;

            for (int distance = 1; distance <= beam.range(); distance++) {
                cursor.setWithOffset(base, beam.facing().getStepX() * distance,
                        beam.facing().getStepY() * distance, beam.facing().getStepZ() * distance);
                if (!level.isLoaded(cursor)) {
                    // Nothing can be read here without loading a chunk to do it. What that means depends on whose
                    // grid this is. A ship mod gives a grid a plot of chunks and keeps loaded only the ones its
                    // blocks are in, so a column that leaves those has simply left the ship: the rest of it is
                    // empty, and what lies along it out in the world is for the rays to find. Counting that as
                    // the end of the column cut every beam that ran across its ship, rather than down through
                    // it, to the few blocks between the panel and the edge of its chunk. Above and below the
                    // build limits is empty in any grid. Only in the world proper is an unloaded chunk terrain
                    // that is really there and cannot be seen, and there the column does end.
                    if (onGrid || level.isOutsideBuildHeight(cursor)) {
                        continue;
                    }
                    result.reach[column] = distance - 1;
                    if (result.targets[column] == null) {
                        result.hits[column] = ColumnHit.BLOCKED;
                    }
                    break;
                }
                boolean hadTarget = result.targets[column] != null;
                boolean stopped = result.look(level, column, cursor, pull);
                if (!hadTarget && result.targets[column] != null) {
                    targetDistance = distance;
                }
                if (stopped) {
                    result.reach[column] = distance - 1;
                    break;
                }
            }

            if (crossesGrids) {
                result.castDown(level, beam, column, pull, targetDistance);
            }
        }
        return result;
    }

    /** Takes in the block at {@code pos}. True when it is solid enough to end the column. */
    private boolean look(Level level, int column, BlockPos pos, Direction pull) {
        BlockState state = level.getBlockState(pos);
        if (passes(state)) {
            return false;
        }
        if (targets[column] == null) {
            targets[column] = pos.immutable();
            hits[column] = classify(level, pos, state, pull);
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Casts rays down {@code column} for what other grids hold in it, over the stretch the walk of the panel's
     * own grid found clear. The nearest thing any ray touches becomes the column's target if it is nearer than
     * the one the walk found, at {@code walkedTargetDistance}; and if it is solid, the column ends there.
     */
    private void castDown(Level level, BeamGeometry beam, int column, Direction pull, int walkedTargetDistance) {
        if (reach[column] <= 0) {
            return;
        }
        Vec3 axisA = Vec3.atLowerCornerOf(Direction.get(Direction.AxisDirection.POSITIVE, beam.axisA()).getNormal());
        Vec3 axisB = Vec3.atLowerCornerOf(Direction.get(Direction.AxisDirection.POSITIVE, beam.axisB()).getNormal());
        Vec3 out = Vec3.atLowerCornerOf(beam.facing().getNormal());
        // The centre of the mouth face of this column's panel block.
        Vec3 mouth = Vec3.atCenterOf(beam.columnBase(column)).add(out.scale(0.5));

        BlockPos nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (double[] ray : RAYS) {
            Vec3 line = mouth.add(axisA.scale(ray[0])).add(axisB.scale(ray[1]));
            BlockPos touched = cast(level, beam, line.add(out.scale(RAY_START)),
                    line.add(out.scale(reach[column])), ClipContext.Block.OUTLINE);
            if (touched == null) {
                continue;
            }
            int distance = distanceOut(level, beam, touched);
            // A ray ending exactly on the face of the block past the reach can report it: not the beam's to take.
            if (distance > reach[column]) {
                continue;
            }
            if (distance < nearestDistance) {
                nearest = touched;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return;
        }

        BlockState state = level.getBlockState(nearest);
        if (nearestDistance <= walkedTargetDistance) {
            targets[column] = nearest;
            hits[column] = classify(level, nearest, state, pull);
        }
        if (!state.getCollisionShape(level, nearest).isEmpty()) {
            reach[column] = Math.min(reach[column], nearestDistance - 1);
            return;
        }
        // Something with an outline but nothing to it, a flower or a torch: a target, but not the end of the
        // column. The column ends at the first thing a ray would actually collide with.
        BlockPos solid = cast(level, beam, mouth.add(out.scale(RAY_START)), mouth.add(out.scale(reach[column])),
                ClipContext.Block.COLLIDER);
        if (solid != null) {
            reach[column] = Math.min(reach[column], distanceOut(level, beam, solid) - 1);
        }
    }

    /** The first block along a line given in the panel's grid, in whatever grid it is; null when there is none. */
    @Nullable
    private static BlockPos cast(Level level, BeamGeometry beam, Vec3 from, Vec3 to, ClipContext.Block mode) {
        // The ship mods extend clip for lines through the world, so that is where the line is drawn.
        BlockHitResult hit = level.clip(new ClipContext(beam.toWorld(level, from), beam.toWorld(level, to),
                mode, ClipContext.Fluid.NONE, (Entity) null));
        if (hit.getType() != HitResult.Type.BLOCK || passes(level.getBlockState(hit.getBlockPos()))) {
            return null;
        }
        return hit.getBlockPos().immutable();
    }

    /**
     * Which block of the column's length {@code pos} lies in, from 1. Worked out from where the block is rather
     * than from where the ray touched it, which the two ship mods need not report in the same space.
     */
    private static int distanceOut(Level level, BeamGeometry beam, BlockPos pos) {
        Vec3 world = Compat.toWorldPos(level, Vec3.atCenterOf(pos));
        return Math.max(1, Mth.floor(beam.axialDistance(beam.toLocal(level, world))) + 1);
    }

    /** Air and liquid neither stop the beam nor get pulled. */
    private static boolean passes(BlockState state) {
        return state.isAir() || state.getBlock() instanceof LiquidBlock;
    }

    private static ColumnHit classify(Level level, BlockPos pos, BlockState state, Direction pull) {
        if (state.is(IMMUNE_BLOCKS)) {
            return ColumnHit.BLOCKED;
        }
        if (state.getPistonPushReaction() == PushReaction.DESTROY) {
            return ColumnHit.DESTROYABLE;
        }
        // Vanilla's own test, so anything the mod has taught pistons to push is pullable too. Passing the pull
        // direction as the piston's facing is what lets push-only blocks through: a piston may push those.
        if (PistonBaseBlock.isPushable(state, level, pos, pull, false, pull)
                && state.getBlock().asItem() != Items.AIR) {
            return ColumnHit.PULLABLE;
        }
        return ColumnHit.BLOCKED;
    }

    public int columns() {
        return reach.length;
    }

    /** Clear blocks in front of the mouth along {@code column}, up to the beam's range. */
    public int reach(int column) {
        return reach[column];
    }

    /** The furthest any column reaches. */
    public int maxReach() {
        int max = 0;
        for (int columnReach : reach) {
            max = Math.max(max, columnReach);
        }
        return max;
    }

    public ColumnHit hit(int column) {
        return hits[column];
    }

    /** The nearest block down {@code column}, or null when the column is {@link ColumnHit#CLEAR}. */
    @Nullable
    public BlockPos target(int column) {
        return targets[column];
    }
}
