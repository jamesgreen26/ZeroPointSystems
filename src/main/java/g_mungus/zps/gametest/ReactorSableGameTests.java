package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;

import java.util.ArrayList;
import java.util.List;

/**
 * A reactor riding a Sable sublevel. Its cells are in the sublevel's plot, far from where it appears, while
 * anything inside it stands in the world at the place it appears; and once the sublevel is tilted, the cavity
 * no longer lines up with the world's grid at all.
 * <p>
 * Each test builds the shell in the arena, lifts it into a sublevel, freezes it, and turns it. A pig is set
 * down in the world where the middle of the cavity now is, the chamber is lit, and the pig should burn as it
 * would in lava. A second pig in the world just clear of the shell, off a corner of the cavity, should not: once
 * the sublevel is tilted it stands inside the box the cavity sweeps in the world without being in the cavity.
 * <p>
 * Sable is only on the classpath for local runs. Where it is missing the tests pass without doing anything.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ReactorSableGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    private static final String BATCH = "reactorSable";
    private static final int TIMEOUT = 400;

    /** A 5x5x5 shell around a 3x3x3 cavity, in the middle of the arena. */
    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 5, 7);

    @GameTest(template = TEMPLATE, batch = BATCH + "_level", timeoutTicks = TIMEOUT)
    public static void level(GameTestHelper helper) {
        run(helper, 0, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_tipped30", timeoutTicks = TIMEOUT)
    public static void tipped30(GameTestHelper helper) {
        run(helper, 30, 0, 0);
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_skewed", timeoutTicks = TIMEOUT)
    public static void skewed(GameTestHelper helper) {
        run(helper, 30, 40, 15);
    }

    private static DuctNetwork<?> kelvin() {
        return KelvinMod.INSTANCE.forceGetKelvin();
    }

    /**
     * @param tip  degrees about X
     * @param turn degrees about Y
     * @param roll degrees about Z
     */
    private static void run(GameTestHelper helper, double tip, double turn, double roll) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();

        BlockState plating = ModBlocks.REINFORCED_PLATING.get().defaultBlockState();
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean edge = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            if (edge) {
                helper.setBlock(pos, plating);
                blocks.add(helper.absolutePos(pos));
            }
        }
        BlockPos min = helper.absolutePos(MIN);
        BlockPos max = helper.absolutePos(MAX);
        Quaterniond rotation = new Quaterniond()
                .rotateY(Math.toRadians(turn)).rotateX(Math.toRadians(tip)).rotateZ(Math.toRadians(roll));

        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, min, max, blocks, rotation);
            ReactorManager manager = ReactorManager.get(level);

            // Sable moves the blocks without their placement hooks, so the shell is told about itself the way a
            // wall block placed on the sublevel would tell it: from the middle of one face, which has the cavity
            // behind it. The plot keeps the shell's shape, only moved, so that is two out from its centre.
            List<BlockPos> plot = rig.blocksLeft();
            helper.assertTrue(plot.size() == blocks.size(),
                    "The whole shell should be on the sublevel, " + plot.size() + " of " + blocks.size() + " are");
            BlockPos wall = centreOf(plot).east(2);
            helper.assertTrue(level.getBlockState(wall).is(ModBlocks.REINFORCED_PLATING.get()),
                    "The middle of the east face should be plating at plot " + wall.toShortString());
            manager.onWallBlockChanged(level, wall, false);

            List<Reactor> reactors = manager.reactorsAt(wall);
            helper.assertTrue(reactors.size() == 1, "Expected one reactor on the sublevel, found " + reactors.size());
            Reactor reactor = reactors.get(0);
            helper.assertTrue(Compat.gridOf(level, reactor.host()) != null,
                    "The reactor's host should be on the sublevel; " + rig.describePose());

            // Where the cavity is in the world now, and its middle, where the pig goes.
            Vec3 centre = Compat.toWorldPos(level, reactor.host(), reactor.bounds().getCenter());
            Pig inside = pigAt(helper, centre);
            // Out along a diagonal of the world, just clear of the shell. Once the sublevel is tilted, the box the
            // cavity sweeps in the world reaches past its own corners, and this is where that box has room in it
            // for a pig that is nevertheless outside the cavity.
            Pig outside = pigAt(helper, justOutsideTheShell(level, reactor, centre, new Vec3(0, 1, 1).normalize()));

            DuctNodePos host = GasEdgeNegotiator.nodePos(level, reactor.host());
            kelvin().addGasAtTemperature(host, ModGases.AETHER, 0.01, 300.0);
            double target = ZPSConfig.reactorIgnitionTemperatureK() + 5_000.0;
            kelvin().modHeatEnergy(host, (target - kelvin().getTemperatureAt(host)) * kelvin().getNodeHeatCapacity(host));

            helper.runAfterDelay(10, () -> {
                try {
                    helper.assertTrue(reactor.isLit(), "The reactor should register as lit");
                    helper.assertTrue(inside.getHealth() < inside.getMaxHealth(),
                            "The pig in the cavity should have been hurt; " + rig.describePose());
                    helper.assertTrue(inside.isOnFire(), "The pig in the cavity should be on fire");
                    helper.assertTrue(outside.getHealth() == outside.getMaxHealth(),
                            "The pig outside the cavity should be unhurt; " + rig.describePose());
                    helper.assertFalse(outside.isOnFire(), "The pig outside the cavity should not be on fire");
                } finally {
                    inside.discard();
                    outside.discard();
                    rig.remove();
                }
                helper.succeed();
            });
        });
    }

    private static BlockPos centreOf(List<BlockPos> blocks) {
        long x = 0, y = 0, z = 0;
        for (BlockPos pos : blocks) {
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }
        int n = blocks.size();
        return new BlockPos((int) Math.round(x / (double) n), (int) Math.round(y / (double) n), (int) Math.round(z / (double) n));
    }

    /**
     * The nearest point out from {@code centre} along {@code direction} at which a pig's box has every corner
     * outside the shell: past the outer faces of the wall, in the reactor's own grid.
     */
    private static Vec3 justOutsideTheShell(ServerLevel level, Reactor reactor, Vec3 centre, Vec3 direction) {
        AABB shell = reactor.bounds().inflate(1.0);
        double half = EntityType.PIG.getWidth() / 2.0;
        for (double t = 0; t < 16; t += 0.05) {
            Vec3 at = centre.add(direction.scale(t));
            boolean clear = true;
            for (int corner = 0; corner < 8 && clear; corner++) {
                Vec3 world = at.add((corner & 1) == 0 ? -half : half, (corner & 2) == 0 ? -half : half,
                        (corner & 4) == 0 ? -half : half);
                Vec3 local = Compat.toLocalSpaceOf(level, reactor.host(), world);
                clear = !shell.contains(local);
            }
            if (clear) {
                return centre.add(direction.scale(t + 0.05));
            }
        }
        throw new IllegalStateException("No room outside the shell along " + direction);
    }

    /** A pig that stays where it is put, its box centred on {@code worldCentre}. */
    private static Pig pigAt(GameTestHelper helper, Vec3 worldCentre) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG,
                helper.relativeVec(worldCentre.subtract(0, EntityType.PIG.getHeight() / 2.0, 0)));
        pig.setNoGravity(true);
        return pig;
    }
}
