package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.AirCompressorBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.AirCompressorBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.room.Room;
import g_mungus.zps.room.RoomManager;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/**
 * The Air Compressor: draws a fixed volume of the air in front of it into its node for a fixed FE a
 * tick, pauses for free when it cannot, and drains a sealed room it draws from.
 *
 * <p>Every test takes the roof off ({@code skyAccess}): the barriers around an arena would
 * otherwise seal the air in it into one big room.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class AirCompressorGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos COMPRESSOR = new BlockPos(3, 2, 3);
    private static final Direction INTAKE = AirCompressorBlock.INTAKE;
    private static final Direction OUTLET = Direction.WEST;

    private static final String ROOM_TEMPLATE = "gametest/flat_11x9x11";
    /** Stone shell around 3x3x3 of air, with the compressor in its floor drawing from inside. */
    private static final BlockPos ROOM_MIN = new BlockPos(3, 1, 3);
    private static final BlockPos ROOM_MAX = new BlockPos(7, 5, 7);
    private static final BlockPos ROOM_CENTRE = new BlockPos(5, 3, 5);
    private static final BlockPos FLOOR_COMPRESSOR = new BlockPos(5, 1, 5);

    private static AirCompressorBlockEntity place(GameTestHelper helper, BlockPos at, int energy) {
        BlockState state = ModBlocks.AIR_COMPRESSOR.get().defaultBlockState()
                .setValue(AirCompressorBlock.FACING, OUTLET);
        helper.setBlock(at, state);
        BlockEntity blockEntity = helper.getBlockEntity(at);
        if (!(blockEntity instanceof AirCompressorBlockEntity compressor)) {
            helper.fail("Air Compressor has no block entity");
            throw new IllegalStateException();
        }
        compressor.setEnergyStored(energy);
        return compressor;
    }

    private static AirCompressorBlockEntity place(GameTestHelper helper, int energy) {
        return place(helper, COMPRESSOR, energy);
    }

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static GasType air() {
        return GasTypeRegistry.INSTANCE.getGasType(AtmosphericComposition.AIR);
    }

    private static double airAt(GameTestHelper helper, BlockPos relative) {
        Double mass = KelvinMod.INSTANCE.forceGetKelvin().getGasMassAt(node(helper, relative)).get(air());
        return mass == null ? 0 : mass;
    }

    private static double totalAt(GameTestHelper helper, BlockPos relative) {
        return KelvinMod.INSTANCE.forceGetKelvin().getGasMassAt(node(helper, relative)).values().stream()
                .mapToDouble(Double::doubleValue).sum();
    }

    private static int energy(AirCompressorBlockEntity compressor) {
        return compressor.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).getEnergyStored();
    }

    /** What one tick's draw of the open air in front of the compressor weighs. */
    private static double airPerTick(GameTestHelper helper) {
        RoomManager.Draw draw = RoomManager.get(helper.getLevel()).peekGas(helper.getLevel(),
                helper.absolutePos(COMPRESSOR.relative(INTAKE)), ZPSConfig.airCompressorSweptVolume());
        Double mass = draw.kg().get(air());
        return mass == null ? 0 : mass;
    }

    /** Asserts the compressor sat this out: the status it gave, no FE spent and no gas drawn. */
    private static void assertPaused(GameTestHelper helper, AirCompressorBlockEntity compressor, int energyBefore,
                                     AirCompressorBlockEntity.Status expected) {
        helper.assertTrue(compressor.getStatus() == expected,
                "Expected " + expected + ", got " + compressor.getStatus());
        helper.assertTrue(energy(compressor) == energyBefore,
                "A paused compressor should spend nothing, but went from " + energyBefore + " to " + energy(compressor) + " FE");
        helper.assertTrue(totalAt(helper, COMPRESSOR) == 0,
                "A paused compressor should draw nothing, but holds " + totalAt(helper, COMPRESSOR) + " kg");
    }

    /** It joins on its outlet face alone. */
    @GameTest(template = TEMPLATE)
    public static void connectsOnTheBackOnly(GameTestHelper helper) {
        place(helper, 0);
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        for (Direction direction : Direction.values()) {
            BlockPos ductPos = COMPRESSOR.relative(direction);
            helper.setBlock(ductPos, ModBlocks.GAS_DUCT.get().defaultBlockState());
            boolean joined = kelvin.getEdgeBetween(node(helper, COMPRESSOR), node(helper, ductPos)) != null;
            helper.assertTrue(joined == (direction == OUTLET),
                    "Duct " + direction + " of the compressor " + (joined ? "joined" : "did not join"));
        }
        helper.succeed();
    }

    /** Outdoors it draws the air there every tick, for exactly its FE cost each time. */
    @GameTest(template = TEMPLATE)
    public static void outdoors_drawsAirForAFixedCost(GameTestHelper helper) {
        int start = 4000;
        AirCompressorBlockEntity compressor = place(helper, start);
        double perTick = airPerTick(helper);
        helper.assertTrue(perTick > 0, "There should be air to draw outdoors");

        helper.runAfterDelay(20, () -> {
            int cost = AirCompressorBlockEntity.ENERGY_PER_TICK;
            int spent = start - energy(compressor);
            helper.assertTrue(spent > 0 && spent % cost == 0,
                    "FE should go in whole ticks of " + cost + ", but " + spent + " went");
            int ticks = spent / cost;
            double expected = ticks * perTick;
            double held = airAt(helper, COMPRESSOR);
            helper.assertTrue(Math.abs(held - expected) < expected * 0.05 + 1e-6,
                    ticks + " ticks of drawing should hold " + expected + " kg of air, but it holds " + held);
            helper.assertTrue(compressor.getStatus() == AirCompressorBlockEntity.Status.RUNNING,
                    "It should be running, but is " + compressor.getStatus());
            helper.assertBlockProperty(COMPRESSOR, AirCompressorBlock.RUNNING, true);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void noPower_drawsNothing(GameTestHelper helper) {
        AirCompressorBlockEntity compressor = place(helper, AirCompressorBlockEntity.ENERGY_PER_TICK - 1);
        int before = energy(compressor);
        helper.runAfterDelay(10, () -> {
            assertPaused(helper, compressor, before, AirCompressorBlockEntity.Status.NO_POWER);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void blockedIntake_drawsNothing(GameTestHelper helper) {
        AirCompressorBlockEntity compressor = place(helper, 4000);
        helper.setBlock(COMPRESSOR.relative(INTAKE), Blocks.STONE);
        int before = energy(compressor);
        helper.runAfterDelay(10, () -> {
            assertPaused(helper, compressor, before, AirCompressorBlockEntity.Status.BLOCKED);
            helper.assertBlockProperty(COMPRESSOR, AirCompressorBlock.RUNNING, false);
            helper.succeed();
        });
    }

    /** Anything rooms count as letting air through counts as open to the intake too. */
    @GameTest(template = TEMPLATE)
    public static void grateOverTheIntake_stillDraws(GameTestHelper helper) {
        AirCompressorBlockEntity compressor = place(helper, 4000);
        helper.setBlock(COMPRESSOR.relative(INTAKE), Blocks.IRON_BARS);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(compressor.getStatus() == AirCompressorBlockEntity.Status.RUNNING,
                    "A grate over the intake should let it draw, but it is " + compressor.getStatus());
            helper.assertTrue(airAt(helper, COMPRESSOR) > 0, "It should have drawn air through the grate");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void underwater_drawsNothing(GameTestHelper helper) {
        AirCompressorBlockEntity compressor = place(helper, 4000);
        helper.setBlock(COMPRESSOR.relative(INTAKE), Blocks.WATER);
        int before = energy(compressor);
        helper.runAfterDelay(10, () -> {
            assertPaused(helper, compressor, before, AirCompressorBlockEntity.Status.BLOCKED);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void redstone_shutsIt(GameTestHelper helper) {
        AirCompressorBlockEntity compressor = place(helper, 4000);
        helper.setBlock(COMPRESSOR.north(), Blocks.REDSTONE_BLOCK);
        int before = energy(compressor);
        helper.runAfterDelay(10, () -> {
            helper.assertBlockProperty(COMPRESSOR, AirCompressorBlock.POWERED, true);
            assertPaused(helper, compressor, before, AirCompressorBlockEntity.Status.SHUT);
            helper.succeed();
        });
    }

    /**
     * Its outlet dead-ended, it fills to just under the cutoff and waits there, spending nothing,
     * then picks up again by itself once gas is let out.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void pressureShutoff_pausesAndResumes(GameTestHelper helper) {
        AirCompressorBlockEntity compressor = place(helper, 8000);
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos nodePos = node(helper, COMPRESSOR);
        double ceiling = AirCompressorBlock.MAX_PRESSURE;
        // Most of the way there already, so the test does not wait on a thousand ticks of drawing.
        kelvin.addGasAtTemperature(nodePos, air(), 80.0, 293.0);
        int[] energyWhenFull = new int[1];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    double pressure = kelvin.getPressureAt(nodePos);
                    helper.assertTrue(pressure <= ceiling * (AirCompressorBlockEntity.FULL_FRACTION + 0.005),
                            "Pressure overshot the cutoff: " + pressure + " Pa");
                    helper.assertTrue(compressor.getStatus() == AirCompressorBlockEntity.Status.OUTPUT_FULL,
                            "Still filling at " + pressure + " Pa: " + compressor.getStatus());
                })
                .thenExecute(() -> {
                    double pressure = kelvin.getPressureAt(nodePos);
                    helper.assertTrue(pressure > ceiling * 0.85,
                            "It stopped well short of the cutoff, at " + pressure + " Pa");
                    energyWhenFull[0] = energy(compressor);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(energy(compressor) == energyWhenFull[0],
                        "A full compressor should spend nothing"))
                .thenExecute(() -> kelvin.removeGas(nodePos, air(), 20.0))
                .thenWaitUntil(() -> helper.assertTrue(compressor.getStatus() == AirCompressorBlockEntity.Status.RUNNING,
                        "It should pick up again once the pressure drops, but is " + compressor.getStatus()))
                .thenSucceed();
    }

    /** Drawing from a sealed room takes the gas out of it, and keeps the room awake while it does. */
    @GameTest(template = ROOM_TEMPLATE, timeoutTicks = 200)
    public static void sealedRoom_isDrained(GameTestHelper helper) {
        for (int x = ROOM_MIN.getX(); x <= ROOM_MAX.getX(); x++) {
            for (int y = ROOM_MIN.getY(); y <= ROOM_MAX.getY(); y++) {
                for (int z = ROOM_MIN.getZ(); z <= ROOM_MAX.getZ(); z++) {
                    boolean wall = x == ROOM_MIN.getX() || x == ROOM_MAX.getX() || y == ROOM_MIN.getY()
                            || y == ROOM_MAX.getY() || z == ROOM_MIN.getZ() || z == ROOM_MAX.getZ();
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        RoomManager rooms = RoomManager.get(helper.getLevel());
        // The test world is reused between runs, and a room saved by an earlier run would still be here.
        Room stale = rooms.knownRoomAt(helper.absolutePos(ROOM_CENTRE));
        if (stale != null) {
            rooms.dissolve(stale);
        }
        place(helper, FLOOR_COMPRESSOR, 8000);

        double[] sealedKg = new double[1];
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    Room room = rooms.roomAt(helper.absolutePos(ROOM_CENTRE));
                    helper.assertTrue(room != null, "Drawing from a sealed box should make it a room");
                    sealedKg[0] = room.totalMassKg();
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    Room room = rooms.roomAt(helper.absolutePos(ROOM_CENTRE));
                    helper.assertTrue(room != null && room.isActive(), "The room should stay awake while it is drawn on");
                    double now = room.totalMassKg();
                    helper.assertTrue(now < sealedKg[0] * 0.9,
                            "The room should have thinned, but went from " + sealedKg[0] + " kg to " + now);
                    helper.assertTrue(airAt(helper, FLOOR_COMPRESSOR) > 0, "The room's air should be in the compressor");
                })
                .thenSucceed();
    }
}
