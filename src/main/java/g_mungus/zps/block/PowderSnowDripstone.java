package g_mungus.zps.block;

import g_mungus.zps.mixin.PointedDripstoneBlockInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Lets a stalactite drip powder snow into a cauldron, mirroring how vanilla drips water: the
 * source block sits on top of the block the stalactite hangs from, each random tick has the water
 * fill chance of dripping, and the cauldron under the tip fills one layer per drip.
 *
 * <p>Vanilla models the source as a {@code Fluid}, which powder snow is not, so the check and the
 * cauldron update are reimplemented here and hooked in ahead of the vanilla code paths by
 * {@code PointedDripstoneBlockMixin} and {@code AbstractCauldronBlockMixin}.
 */
public final class PowderSnowDripstone {

    /** Vanilla's per-random-tick chance that a water-fed stalactite drips. */
    private static final float FILL_CHANCE = 0.17578125F;

    /** Vanilla's search range for tips, roots and cauldrons. */
    private static final int SEARCH_RANGE = 11;

    /** Ticks between a drip leaving the tip and landing, before the per-block fall distance. */
    private static final int DRIP_FALL_TICKS = 50;

    /** Vanilla's ambient drip particle chance for a stalactite that can fill a cauldron. */
    private static final float DRIP_PARTICLE_CHANCE = 0.12F;

    private PowderSnowDripstone() {
    }

    /**
     * Random-tick hook. {@code pos} is the topmost dripstone of a stalactite; {@code randChance}
     * is the roll vanilla made for this tick.
     *
     * @return true when powder snow is the source above this stalactite, so vanilla's fluid
     *         handling should be skipped whether or not a drip happened
     */
    public static boolean maybeDrip(BlockState state, ServerLevel level, BlockPos pos, float randChance) {
        if (!PointedDripstoneBlockInvoker.zps$isStalactiteStartPos(state, level, pos)) {
            return false;
        }
        if (!isPowderSnowSource(level, pos.above())) {
            return false;
        }
        if (randChance >= FILL_CHANCE) {
            return true;
        }
        BlockPos tip = PointedDripstoneBlockInvoker.zps$findTip(state, level, pos, SEARCH_RANGE, false);
        if (tip == null) {
            return true;
        }
        BlockPos cauldron = findFillableCauldronBelow(level, tip);
        if (cauldron == null) {
            return true;
        }
        Vec3 drip = dripPoint(level, tip, level.getBlockState(tip));
        level.sendParticles(ParticleTypes.SNOWFLAKE, drip.x, drip.y, drip.z, 3, 0.0, 0.0, 0.0, 0.0);
        int delay = DRIP_FALL_TICKS + (tip.getY() - cauldron.getY());
        level.scheduleTick(cauldron, level.getBlockState(cauldron).getBlock(), delay);
        return true;
    }

    /**
     * Cauldron scheduled-tick hook, run when a drip lands.
     *
     * @return true when the stalactite above is fed by powder snow, so vanilla's fluid handling
     *         should be skipped
     */
    public static boolean receiveDrip(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos tip = PointedDripstoneBlock.findStalactiteTipAboveCauldron(level, pos);
        if (tip == null || !hasPowderSnowSource(level, tip)) {
            return false;
        }
        BlockState filled;
        if (state.is(Blocks.CAULDRON)) {
            filled = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState();
        } else if (canReceive(state)) {
            filled = state.cycle(LayeredCauldronBlock.LEVEL);
        } else {
            return true;
        }
        level.setBlockAndUpdate(pos, filled);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(filled));
        level.playSound(null, pos, SoundEvents.POWDER_SNOW_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.2, 0.0, 0.2, 0.0);
        return true;
    }

    /**
     * Client-side ambient particle hook. Vanilla only shows the frequent drip for fluids it can
     * fill a cauldron with; give a powder-snow-fed tip the same treatment with snowflakes.
     *
     * @return true when this tip is fed by powder snow and its particles were handled here
     */
    public static boolean animateDrip(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!PointedDripstoneBlock.canDrip(state) || !hasPowderSnowSource(level, pos)) {
            return false;
        }
        if (random.nextFloat() <= DRIP_PARTICLE_CHANCE) {
            Vec3 drip = dripPoint(level, pos, state);
            level.addParticle(ParticleTypes.SNOWFLAKE, drip.x, drip.y, drip.z, 0.0, 0.0, 0.0);
        }
        return true;
    }

    /** Whether the stalactite containing {@code stalactitePos} hangs from a block with powder snow on top. */
    public static boolean hasPowderSnowSource(Level level, BlockPos stalactitePos) {
        BlockState state = level.getBlockState(stalactitePos);
        if (!state.is(Blocks.POINTED_DRIPSTONE)
                || state.getValue(PointedDripstoneBlock.TIP_DIRECTION) != Direction.DOWN) {
            return false;
        }
        return PointedDripstoneBlockInvoker.zps$findRootBlock(level, stalactitePos, state, SEARCH_RANGE)
                .map(support -> isPowderSnowSource(level, support))
                .orElse(false);
    }

    /** Vanilla reads its fluid from the block above the stalactite's supporting block; do the same. */
    private static boolean isPowderSnowSource(Level level, BlockPos supportPos) {
        return level.getBlockState(supportPos.above()).is(Blocks.POWDER_SNOW);
    }

    private static boolean canReceive(BlockState state) {
        return state.is(Blocks.POWDER_SNOW_CAULDRON)
                && state.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL;
    }

    /** Same walk as vanilla's {@code findFillableCauldronBelowStalactiteTip}, for our cauldron predicate. */
    @Nullable
    private static BlockPos findFillableCauldronBelow(ServerLevel level, BlockPos tip) {
        BlockPos.MutableBlockPos cursor = tip.mutable();
        for (int i = 1; i < SEARCH_RANGE; i++) {
            cursor.move(Direction.DOWN);
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.CAULDRON) || canReceive(state)) {
                return cursor.immutable();
            }
            if (level.isOutsideBuildHeight(cursor.getY())
                    || !PointedDripstoneBlockInvoker.zps$canDripThrough(level, cursor, state)) {
                return null;
            }
        }
        return null;
    }

    /** Where vanilla spawns a tip's drip particle. */
    private static Vec3 dripPoint(Level level, BlockPos tip, BlockState tipState) {
        Vec3 offset = tipState.getOffset(level, tip);
        return new Vec3(
                tip.getX() + 0.5 + offset.x,
                (tip.getY() + 1) - 0.6875 - 0.0625,
                tip.getZ() + 0.5 + offset.z);
    }
}
