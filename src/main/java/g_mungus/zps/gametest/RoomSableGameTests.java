package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.GasGaugeBlock;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.room.Room;
import g_mungus.zps.room.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rooms riding a Sable sublevel. The room's cells are in the sublevel's plot, far from where it appears, while
 * whoever is in it stands in the world where it appears; and the outside air around anything on a sublevel is
 * the air where the sublevel is, not the air at its plot.
 * <p>
 * Sable is only on the classpath for local runs. Where it is missing the tests pass without doing anything.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class RoomSableGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    private static final String BATCH = "roomSable";
    private static final int TIMEOUT = 400;

    /** A 5x5x5 stone shell around 3x3x3 of air, in the middle of the arena. */
    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 5, 7);

    private static final ResourceLocation FLUX = ModGases.FLUX.getResourceLocation();

    /**
     * A pig standing in the world where a tilted sublevel's room now is breathes the room; one standing clear
     * of the sublevel breathes the air.
     */
    @GameTest(template = TEMPLATE, batch = BATCH + "_entity", timeoutTicks = TIMEOUT)
    public static void entityOnASublevel_breathesItsRoom(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
            boolean edge = pos.getX() == MIN.getX() || pos.getX() == MAX.getX()
                    || pos.getY() == MIN.getY() || pos.getY() == MAX.getY()
                    || pos.getZ() == MIN.getZ() || pos.getZ() == MAX.getZ();
            if (edge) {
                helper.setBlock(pos, Blocks.STONE);
                blocks.add(helper.absolutePos(pos));
            }
        }
        BlockPos min = helper.absolutePos(MIN);
        BlockPos max = helper.absolutePos(MAX);

        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, min, max, blocks,
                    new Quaterniond().rotateX(Math.toRadians(30)));
            // Sable works out where a sublevel reaches in the world on its own tick, not when it is posed.
            helper.runAfterDelay(5, () -> {
                RoomManager manager = RoomManager.get(level);
                BlockPos plotCentre = centreOf(rig.blocksLeft());
                manager.addGas(level, plotCentre, Map.of(ModGases.FLUX, 50.0), 300.0);
                Room room = manager.roomAt(plotCentre);
                helper.assertTrue(room != null, "Gas into the shell on the sublevel should make a room in its plot; "
                        + rig.describePose());

                Pig inside = pigAt(helper, Compat.toWorldPos(level, plotCentre, Vec3.atCenterOf(plotCentre)));
                Pig outside = pigAt(helper, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
                try {
                    helper.assertTrue(manager.roomAround(inside) == room, "The pig in the shell should be in its room; "
                            + rig.describePose());
                    Map<ResourceLocation, Double> breathed = AtmosphereExposure.atmosphere(inside).fractions();
                    helper.assertTrue(breathed.getOrDefault(FLUX, 0.0) > 0, "The pig in the shell should breathe its flux, has "
                            + breathed + "; " + rig.describePose());
                    helper.assertTrue(manager.roomAround(outside) == null, "The pig clear of the sublevel should be in no room");
                    helper.assertTrue(AtmosphereExposure.atmosphere(outside).fractions().getOrDefault(FLUX, 0.0) == 0,
                            "The pig clear of the sublevel should breathe the air");
                } finally {
                    inside.discard();
                    outside.discard();
                    manager.dissolve(room);
                    rig.remove();
                }
                helper.succeed();
            });
        });
    }

    /**
     * A loose gauge on a sublevel flown high, where the air is thin, reads that thin air rather than the air
     * at the sublevel's plot, which sits low down near the ground.
     */
    @GameTest(template = TEMPLATE, batch = BATCH + "_gauge", timeoutTicks = TIMEOUT)
    public static void looseGaugeOnAHighSublevel_readsTheAirWhereItIs(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(5, 1, 5);
        helper.setBlock(base, Blocks.STONE);
        helper.setBlock(base.above(), ModBlocks.GAS_GAUGE.get().defaultBlockState().setValue(GasGaugeBlock.FACING, Direction.UP));
        BlockPos min = helper.absolutePos(base);
        BlockPos max = helper.absolutePos(base.above());
        BlockPos ground = helper.absolutePos(new BlockPos(1, 2, 1));
        double groundPressure = RoomManager.get(level).ambientAt(level, ground).pressurePa();

        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, min, max, List.of(min, max), new Quaterniond(),
                    new Vector3d(min.getX() + 0.5, 1500, min.getZ() + 0.5));
            // The gauge reads its own empty node until it has ticked on the sublevel; give it time.
            awaitReading(helper, level, rig, groundPressure, 100);
        });
    }

    private static void awaitReading(GameTestHelper helper, ServerLevel level, SableBeamRig rig,
                                     double groundPressure, int ticksLeft) {
        helper.runAfterDelay(1, () -> {
            GasGaugeBlockEntity gauge = null;
            for (BlockPos pos : rig.blocksLeft()) {
                if (level.getBlockEntity(pos) instanceof GasGaugeBlockEntity found) {
                    gauge = found;
                }
            }
            if (gauge == null || gauge.getPressure() <= 0) {
                if (ticksLeft > 0) {
                    awaitReading(helper, level, rig, groundPressure, ticksLeft - 1);
                    return;
                }
                String why = gauge == null ? "The gauge should be on the sublevel" : "The gauge never read anything";
                rig.remove();
                helper.fail(why);
                return;
            }
            try {
                BlockPos plot = gauge.getBlockPos();
                BlockPos world = BlockPos.containing(Compat.toWorldPos(level, plot, Vec3.atCenterOf(plot)));
                helper.assertTrue(world.getY() > 1400, "The gauge should be up high, is at " + world + "; " + rig.describePose());

                double expected = RoomManager.get(level).ambientAt(level, world).pressurePa();
                double reading = gauge.getPressure();
                helper.assertTrue(expected < groundPressure * 0.8, "The air up at " + world.getY() + " should be thinner than the "
                        + groundPressure + " Pa at the ground, is " + expected + " Pa");
                helper.assertTrue(Math.abs(reading - expected) < expected * 0.01, "The gauge should read the air where it is, "
                        + expected + " Pa, not " + reading + " Pa (the plot is at y " + plot.getY() + ")");
            } finally {
                rig.remove();
            }
            helper.succeed();
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

    /** A pig that stays where it is put, its box centred on {@code worldCentre}. */
    private static Pig pigAt(GameTestHelper helper, Vec3 worldCentre) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG,
                helper.relativeVec(worldCentre.subtract(0, EntityType.PIG.getHeight() / 2.0, 0)));
        pig.setNoGravity(true);
        return pig;
    }

    /**
     * A torch on a sublevel keeps burning in the air it starts in, and goes out once the sublevel is flown up
     * into vacuum, though its plot stays down in the air.
     */
    @GameTest(template = TEMPLATE, batch = BATCH + "_torch", timeoutTicks = TIMEOUT)
    public static void torchOnASublevelFlownIntoVacuum_goesOut(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(5, 1, 5);
        helper.setBlock(base, Blocks.STONE);
        helper.setBlock(base.above(), Blocks.TORCH);
        BlockPos min = helper.absolutePos(base);
        BlockPos max = helper.absolutePos(base.above());

        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, min, max, List.of(min, max), new Quaterniond());
            // A burn or two in the air it was assembled in.
            helper.runAfterDelay(45, () -> {
                BlockPos torch = torchOn(level, rig);
                if (torch == null || !level.getBlockState(torch).is(Blocks.TORCH)) {
                    rig.remove();
                    helper.fail("The torch should still burn in the air; " + rig.describePose());
                    return;
                }
                rig.moveTo(new Vector3d(min.getX() + 0.5, 2100, min.getZ() + 0.5));
                helper.runAfterDelay(45, () -> {
                    try {
                        helper.assertTrue(level.getBlockState(torch).is(ModBlocks.EXTINGUISHED_TORCH.get()),
                                "The torch should have gone out in vacuum, is " + level.getBlockState(torch) + "; " + rig.describePose());
                    } finally {
                        rig.remove();
                    }
                    helper.succeed();
                });
            });
        });
    }

    private static BlockPos torchOn(ServerLevel level, SableBeamRig rig) {
        for (BlockPos pos : rig.blocksLeft()) {
            if (!level.getBlockState(pos).is(Blocks.STONE)) {
                return pos;
            }
        }
        return null;
    }
}
