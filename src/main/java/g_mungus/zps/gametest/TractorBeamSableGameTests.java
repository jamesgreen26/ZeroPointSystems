package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.BeamCollectorBlock;
import g_mungus.zps.blockentity.BeamCollectorBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.tractor.BeamGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

/**
 * A Tractor Beam riding a Sable sublevel, pulling blocks out of the world: the case that cannot be reasoned into
 * working, because a column of the beam then runs at an angle through the world's grid.
 * <p>
 * Every test is the same experiment with different numbers. A flat plane of ground. Above it a panel facing
 * straight down, lifted into a sublevel, frozen, and tipped over by some angle. The beam is left to
 * run, and the test passes when no block whose centre lies inside the beam is left: all of them, not some.
 * <p>
 * The tests ask for sky access. The framework otherwise roofs each arena with barrier blocks, which nothing can
 * pull, and a panel posed above the roof would be blocked by it and prove nothing.
 * <p>
 * Sable is only on the classpath for local runs. Where it is missing the tests pass without doing anything.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TractorBeamSableGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    private static final String BATCH = "tractorBeamSable";
    /**
     * The ground is a thick slab of dirt floating above the arena, wider than it on every side. Thick, because the
     * hard part is not the surface: a tipped beam digs a slanted shaft and has to keep finding blocks down it.
     * Wide, so that the shaft stays in the ground instead of coming out of its side after a few blocks. And above
     * the arena because the framework walls every arena in with barrier blocks, which nothing can pull and which
     * would shield whatever a slanted beam reached through them.
     * <p>
     * It overhangs the neighbouring arenas, which is why every test here is a batch of its own: no two run at once.
     */
    private static final int GROUND_BOTTOM = 10;
    private static final int GROUND_DEPTH = 10;
    private static final int GROUND_TOP = GROUND_BOTTOM + GROUND_DEPTH - 1;
    /** A positive tip swings the beam toward -Z, so that is the way the ground runs on. */
    private static final int GROUND_MIN_X = -14;
    private static final int GROUND_MAX_X = 24;
    private static final int GROUND_MIN_Z = -44;
    private static final int GROUND_MAX_Z = 18;
    /** The arena itself, which the panel is centred over. */
    private static final int SIZE = 11;
    private static final int TIMEOUT = 2400;

    // --- straight down, as a baseline: the world's grid and the sublevel's line up ---------------------

    @GameTest(template = TEMPLATE, batch = BATCH + "_single_level_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void single_level_near(GameTestHelper helper) {
        run(helper, 1, 3, 0, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_level_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_level_far(GameTestHelper helper) {
        run(helper, 3, 5, 0, 0, 0);
    }

    // --- tipped about one axis ----------------------------------------------------------------------

    @GameTest(template = TEMPLATE, batch = BATCH + "_single_tipped15_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void single_tipped15_near(GameTestHelper helper) {
        run(helper, 1, 3, 15, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_single_tipped30_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void single_tipped30_far(GameTestHelper helper) {
        run(helper, 1, 5, 30, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_single_tipped45_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void single_tipped45_near(GameTestHelper helper) {
        run(helper, 1, 3, 45, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel2x2_tipped30_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel2x2_tipped30_near(GameTestHelper helper) {
        run(helper, 2, 3, 30, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_tipped20_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_tipped20_far(GameTestHelper helper) {
        run(helper, 3, 5, 20, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_tipped45_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_tipped45_near(GameTestHelper helper) {
        run(helper, 3, 3, 45, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_tipped30_veryFar", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_tipped30_veryFar(GameTestHelper helper) {
        run(helper, 3, 10, 30, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel2x2_tipped10_veryFar", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel2x2_tipped10_veryFar(GameTestHelper helper) {
        run(helper, 2, 8, 10, 0, 0);
    }

    // --- steep: the beam skims the ground, and its rays run almost along the surface they have to find blocks in

    @GameTest(template = TEMPLATE, batch = BATCH + "_single_tipped60_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void single_tipped60_near(GameTestHelper helper) {
        run(helper, 1, 2, 60, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_tipped60_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_tipped60_far(GameTestHelper helper) {
        run(helper, 3, 8, 60, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_tipped75_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_tipped75_near(GameTestHelper helper) {
        run(helper, 3, 3, 75, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel2x2_steepSkewed_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel2x2_steepSkewed_near(GameTestHelper helper) {
        run(helper, 2, 3, 65, 25, 20);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_grazing80", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_grazing80(GameTestHelper helper) {
        run(helper, 3, 2, 80, 0, 0);
    }

    // --- long: near the end of a 3x3 panel's 32 blocks --------------------------------------------------

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_level_long", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_level_long(GameTestHelper helper) {
        run(helper, 3, 22, 0, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_tipped25_long", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_tipped25_long(GameTestHelper helper) {
        run(helper, 3, 20, 25, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_skewed_long", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_skewed_long(GameTestHelper helper) {
        run(helper, 3, 16, 35, 15, 10);
    }

    // --- tipped and turned, so that nothing lines up with anything ---------------------------------------

    @GameTest(template = TEMPLATE, batch = BATCH + "_single_skewed_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void single_skewed_far(GameTestHelper helper) {
        run(helper, 1, 5, 25, 35, 10);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel2x2_skewed_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel2x2_skewed_near(GameTestHelper helper) {
        run(helper, 2, 3, 35, 50, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_skewed_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_skewed_far(GameTestHelper helper) {
        run(helper, 3, 5, 30, 20, 15);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_panel3x3_turnedOnly_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void panel3x3_turnedOnly_near(GameTestHelper helper) {
        run(helper, 3, 3, 0, 33, 0);
    }

    // --- a panel facing sideways on its sublevel, with the sublevel turned to aim it at the ground. To the world
    // these are the same beams as above. To the panel they are not: its beam now runs across its own grid rather
    // than down through it, and out past the edge of what Sable keeps loaded of the sublevel's plot. ---------------

    @GameTest(template = TEMPLATE, batch = BATCH + "_sideways_single_near", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void sideways_single_near(GameTestHelper helper) {
        run(helper, Direction.NORTH, 1, 2, -90, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_sideways_single_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void sideways_single_far(GameTestHelper helper) {
        run(helper, Direction.NORTH, 1, 6, -90, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_sideways_panel3x3_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void sideways_panel3x3_far(GameTestHelper helper) {
        run(helper, Direction.NORTH, 3, 12, -90, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_sideways_panel3x3_long", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void sideways_panel3x3_long(GameTestHelper helper) {
        run(helper, Direction.NORTH, 3, 22, -90, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_sideways_panel3x3_tilted", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void sideways_panel3x3_tilted(GameTestHelper helper) {
        run(helper, Direction.NORTH, 3, 10, -60, 20, 10);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_sideways_east_panel2x2_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void sideways_east_panel2x2_far(GameTestHelper helper) {
        // East is +X; a roll of -90 about Z brings +X round to point down.
        run(helper, Direction.EAST, 2, 10, 0, 0, -90);
    }

    // --- the other way round: a panel in the world, pulling a tilted sublevel apart -------------------------

    @GameTest(template = TEMPLATE, batch = BATCH + "_fromSubLevel_single_level", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void fromSubLevel_single_level(GameTestHelper helper) {
        runFromSubLevel(helper, 1, 2, 0, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_fromSubLevel_single_tipped30", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void fromSubLevel_single_tipped30(GameTestHelper helper) {
        runFromSubLevel(helper, 1, 3, 30, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_fromSubLevel_panel2x2_skewed", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void fromSubLevel_panel2x2_skewed(GameTestHelper helper) {
        runFromSubLevel(helper, 2, 5, 35, 50, 15);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_fromSubLevel_panel3x3_level", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void fromSubLevel_panel3x3_level(GameTestHelper helper) {
        runFromSubLevel(helper, 3, 6, 0, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_fromSubLevel_panel3x3_tipped45", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void fromSubLevel_panel3x3_tipped45(GameTestHelper helper) {
        runFromSubLevel(helper, 3, 8, 45, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_fromSubLevel_panel3x3_skewed_far", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void fromSubLevel_panel3x3_skewed_far(GameTestHelper helper) {
        runFromSubLevel(helper, 3, 18, 30, 20, 25);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_fromSubLevel_panel3x3_turnedOnly", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void fromSubLevel_panel3x3_turnedOnly(GameTestHelper helper) {
        runFromSubLevel(helper, 3, 6, 0, 33, 0);
    }

    /**
     * A panel set in the arena's floor, mouth up, under a slab of dirt that has been lifted into a sublevel,
     * frozen, and tilted. Passes when no block of the slab whose centre lies inside the beam is left.
     *
     * @param height clear blocks between the panel and the slab before it is tilted
     */
    private static void runFromSubLevel(GameTestHelper helper, int width, int height, double tip, double turn,
                                        double roll) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();

        int corner = (SIZE - width) / 2;
        int panelY = 2;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                helper.setBlock(new BlockPos(corner + x, panelY, corner + z), ModBlocks.BEAM_COLLECTOR.get()
                        .defaultBlockState().setValue(BeamCollectorBlock.FACING, Direction.UP));
            }
        }
        helper.setBlock(new BlockPos(corner, panelY - 1, corner), Blocks.REDSTONE_BLOCK);

        // The slab: the width of the arena, three thick.
        int slabY = panelY + 1 + height;
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                for (int y = slabY; y < slabY + 3; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    helper.setBlock(pos, Blocks.DIRT);
                    blocks.add(helper.absolutePos(pos));
                }
            }
        }
        BlockPos min = helper.absolutePos(new BlockPos(1, slabY, 1));
        BlockPos max = helper.absolutePos(new BlockPos(SIZE - 2, slabY + 2, SIZE - 2));
        Quaterniond rotation = new Quaterniond()
                .rotateY(Math.toRadians(turn)).rotateX(Math.toRadians(tip)).rotateZ(Math.toRadians(roll));

        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, min, max, blocks, rotation);
            int[] startedWith = {-1};

            helper.succeedWhen(() -> {
                if (!(helper.getBlockEntity(new BlockPos(corner, panelY, corner)) instanceof BeamCollectorBlockEntity beam)
                        || beam.getWidth() != width) {
                    throw new GameTestAssertException("The panel has not formed yet");
                }
                IEnergyStorage energy = beam.getEnergyStorage(null);
                if (energy != null) {
                    energy.receiveEnergy(Integer.MAX_VALUE, false);
                }

                BeamGeometry geometry = beam.geometry();
                List<BlockPos> left = new ArrayList<>();
                for (BlockPos pos : rig.blocksLeft()) {
                    Vec3 local = geometry.toLocal(level, Compat.toWorldPos(level, Vec3.atCenterOf(pos)));
                    double out = geometry.axialDistance(local);
                    if (geometry.columnAt(local) >= 0 && out > 0 && out <= geometry.range()) {
                        left.add(pos);
                    }
                }
                if (startedWith[0] < 0) {
                    startedWith[0] = left.size();
                    helper.assertTrue(startedWith[0] > 0,
                            "The beam is not aimed at the slab at all, so the test would prove nothing; " + rig.describePose());
                }
                helper.assertTrue(beam.isActive(), "The beam is not running");
                if (!left.isEmpty()) {
                    throw new GameTestAssertException(left.size() + " of " + startedWith[0]
                            + " blocks of the sublevel in the beam were never taken, first at plot "
                            + left.get(0).toShortString() + "; " + rig.describePose());
                }
                ZPSMod.LOGGER.info("[tractor beam under sublevel] {}x{} height {} tip {} turn {} roll {}: took all {} "
                        + "blocks in the beam by tick {}", width, width, height, tip, turn, roll, startedWith[0], helper.getTick());
                rig.remove();
            });
        });
    }

    // --- the experiment ------------------------------------------------------------------------------

    /**
     * @param width  panel blocks per side
     * @param height clear blocks between the dirt plane and the panel before it is tipped
     * @param tip    degrees about X, which swings the beam along Z
     * @param turn   degrees about Y
     * @param roll   degrees about Z, which swings the beam along X
     */
    private static void run(GameTestHelper helper, int width, int height, double tip, double turn, double roll) {
        run(helper, Direction.DOWN, width, height, tip, turn, roll);
    }

    /**
     * @param facing the way the panel faces on its own sublevel, before the sublevel is turned. Whatever it is,
     *               the turning has to bring it round to point at the ground.
     */
    private static void run(GameTestHelper helper, Direction facing, int width, int height, double tip, double turn,
                            double roll) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();

        // Laid afresh over whatever an earlier test left up here, failed ones included: nothing outside an arena
        // is cleared for us.
        for (int x = GROUND_MIN_X; x <= GROUND_MAX_X; x++) {
            for (int z = GROUND_MIN_Z; z <= GROUND_MAX_Z; z++) {
                for (int y = GROUND_BOTTOM; y <= GROUND_TOP; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
                }
            }
        }

        // The panel, centred over the plane, with a redstone block on its back to run it. It is built facing
        // whichever way was asked for, in the two axes across that, so its blocks can join up.
        int corner = (SIZE - width) / 2;
        int panelY = GROUND_TOP + 1 + height;
        BlockPos origin = new BlockPos(corner, panelY, corner);
        Direction.Axis[] across = java.util.Arrays.stream(Direction.Axis.values())
                .filter(axis -> axis != facing.getAxis()).toArray(Direction.Axis[]::new);
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos lowest = origin.mutable();
        BlockPos.MutableBlockPos highest = origin.mutable();
        for (int a = 0; a < width; a++) {
            for (int b = 0; b < width; b++) {
                BlockPos pos = origin.relative(across[0], a).relative(across[1], b);
                helper.setBlock(pos, ModBlocks.BEAM_COLLECTOR.get().defaultBlockState()
                        .setValue(BeamCollectorBlock.FACING, facing));
                blocks.add(helper.absolutePos(pos));
                grow(lowest, highest, pos);
            }
        }
        BlockPos power = origin.relative(facing.getOpposite());
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        blocks.add(helper.absolutePos(power));
        grow(lowest, highest, power);

        BlockPos min = helper.absolutePos(lowest);
        BlockPos max = helper.absolutePos(highest);
        Quaterniond rotation = new Quaterniond()
                .rotateY(Math.toRadians(turn)).rotateX(Math.toRadians(tip)).rotateZ(Math.toRadians(roll));

        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, min, max, blocks, rotation);
            int[] startedWith = {-1};

            helper.succeedWhen(() -> {
                BeamCollectorBlockEntity beam = rig.findController(width);
                helper.assertTrue(beam != null, "The panel has not formed on the sublevel yet");
                IEnergyStorage energy = beam.getEnergyStorage(null);
                if (energy != null) {
                    energy.receiveEnergy(Integer.MAX_VALUE, false);
                }

                List<BlockPos> left = blocksInBeam(helper, beam.geometry());
                if (startedWith[0] < 0) {
                    startedWith[0] = left.size();
                    helper.assertTrue(startedWith[0] > 0,
                            "The beam is not aimed at the plane at all, so the test would prove nothing; " + rig.describePose());
                }
                helper.assertTrue(beam.isActive(), "The beam is not running; " + rig.describePose());
                helper.assertTrue(left.isEmpty(), left.size() + " of " + startedWith[0]
                        + " blocks in the beam were never taken, at " + describe(helper, left) + "; " + rig.describePose());
                ZPSMod.LOGGER.info("[tractor beam on sublevel] facing " + facing + " {}x{} height {} tip {} turn {} roll {}: took all {} blocks "
                                + "in the beam by tick {}; {}", width, width, height, tip, turn, roll, startedWith[0],
                        helper.getTick(), rig.describePose());
                rig.remove();
                for (int x = GROUND_MIN_X; x <= GROUND_MAX_X; x++) {
                    for (int z = GROUND_MIN_Z; z <= GROUND_MAX_Z; z++) {
                        for (int y = GROUND_BOTTOM; y <= GROUND_TOP; y++) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                        }
                    }
                }
            });
        });
    }

    /**
     * Every block of the ground whose centre is inside the beam: within the panel's footprint, in front of the
     * mouth, and no further out than the beam is set to reach.
     */
    private static List<BlockPos> blocksInBeam(GameTestHelper helper, BeamGeometry beam) {
        List<BlockPos> inside = new ArrayList<>();
        for (int x = GROUND_MIN_X; x <= GROUND_MAX_X; x++) {
            for (int z = GROUND_MIN_Z; z <= GROUND_MAX_Z; z++) {
                for (int y = GROUND_BOTTOM; y <= GROUND_TOP; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).isAir()) {
                        continue;
                    }
                    Vec3 local = beam.toLocal(helper.getLevel(), Vec3.atCenterOf(helper.absolutePos(pos)));
                    double out = beam.axialDistance(local);
                    if (beam.columnAt(local) >= 0 && out > 0 && out <= beam.range()) {
                        inside.add(pos);
                    }
                }
            }
        }
        return inside;
    }

    private static void grow(BlockPos.MutableBlockPos lowest, BlockPos.MutableBlockPos highest, BlockPos pos) {
        lowest.set(Math.min(lowest.getX(), pos.getX()), Math.min(lowest.getY(), pos.getY()), Math.min(lowest.getZ(), pos.getZ()));
        highest.set(Math.max(highest.getX(), pos.getX()), Math.max(highest.getY(), pos.getY()), Math.max(highest.getZ(), pos.getZ()));
    }

    private static String describe(GameTestHelper helper, List<BlockPos> positions) {
        StringBuilder text = new StringBuilder();
        for (BlockPos pos : positions.subList(0, Math.min(positions.size(), 6))) {
            text.append(pos.toShortString()).append(' ').append(helper.getBlockState(pos).getBlock().getName().getString()).append("; ");
        }
        return text.toString();
    }
}
