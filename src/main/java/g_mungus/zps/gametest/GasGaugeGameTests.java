package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.GasGaugeBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity.Mode;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * The gas gauge: it joins the network on its inlet face and nowhere else, keeps what reaches it,
 * maps its reading onto the configured bounds, and puts that on a comparator.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GasGaugeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos GAUGE = new BlockPos(3, 2, 3);
    /** North of the gauge, so a gauge facing south has its inlet pressed against it. */
    private static final BlockPos SOURCE = new BlockPos(3, 2, 2);
    /** South of the gauge, pointing north at it, so the gauge is the comparator's input. */
    private static final BlockPos COMPARATOR = new BlockPos(3, 2, 4);

    /** Comparators re-read their input on a two-tick delay; this covers a couple of them. */
    private static final int COMPARATOR_SETTLE_TICKS = 10;

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static double gasAt(GameTestHelper helper, BlockPos relative) {
        double total = 0;
        for (double mass : KelvinMod.INSTANCE.forceGetKelvin()
                .getGasMassAt(node(helper, relative)).values()) {
            total += mass;
        }
        return total;
    }

    private static GasGaugeBlockEntity placeGauge(GameTestHelper helper, Direction facing) {
        helper.setBlock(GAUGE, ModBlocks.GAS_GAUGE.get().defaultBlockState()
                .setValue(GasGaugeBlock.FACING, facing));
        BlockEntity blockEntity = helper.getBlockEntity(GAUGE);
        if (!(blockEntity instanceof GasGaugeBlockEntity gauge)) {
            helper.fail("The gauge has no block entity");
            throw new IllegalStateException();
        }
        return gauge;
    }

    private static void placeSource(GameTestHelper helper) {
        helper.setBlock(SOURCE, ModBlocks.CREATIVE_GAS_GENERATOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(SOURCE);
        if (!(blockEntity instanceof CreativeGasGeneratorBlockEntity generator)) {
            helper.fail("The gas source has no block entity");
            throw new IllegalStateException();
        }
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.0, 900.0);
    }

    private static boolean joinedToSource(GameTestHelper helper) {
        return KelvinMod.INSTANCE.forceGetKelvin()
                .getEdgeBetween(node(helper, SOURCE), node(helper, GAUGE)) != null;
    }

    /** A comparator reading the gauge, and the gauge alone. */
    private static void placeComparator(GameTestHelper helper) {
        helper.setBlock(COMPARATOR, Blocks.COMPARATOR.defaultBlockState()
                .setValue(ComparatorBlock.FACING, Direction.NORTH));
    }

    private static int comparatorOutput(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(COMPARATOR);
        if (!(blockEntity instanceof ComparatorBlockEntity comparator)) {
            helper.fail("The comparator has no block entity");
            throw new IllegalStateException();
        }
        return comparator.getOutputSignal();
    }

    private static void fillGauge(GameTestHelper helper, double temperature) {
        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, GAUGE), ModGases.FLUX, 1.0, temperature);
    }

    @GameTest(template = TEMPLATE)
    public static void gaugeJoinsOnItsInlet(GameTestHelper helper) {
        // Facing south puts the inlet on the north face, which is the one the source is against.
        placeSource(helper);
        placeGauge(helper, Direction.SOUTH);

        if (!joinedToSource(helper)) {
            helper.fail("No edge formed between the source and the gauge's inlet");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gaugeRefusesEveryFaceButItsInlet(GameTestHelper helper) {
        placeSource(helper);
        placeGauge(helper, Direction.UP);

        if (joinedToSource(helper)) {
            helper.fail("The gauge connected on a face that is not its inlet");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gaugeRefusesItsDialFace(GameTestHelper helper) {
        placeSource(helper);
        placeGauge(helper, Direction.NORTH);

        if (joinedToSource(helper)) {
            helper.fail("The gauge connected on the face its dial is on");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gaugeKeepsItsGas(GameTestHelper helper) {
        placeGauge(helper, Direction.UP);
        fillGauge(helper, 300.0);

        helper.runAfterDelay(40, () -> {
            if (gasAt(helper, GAUGE) < 1.0 - 1e-6) {
                helper.fail("The gauge let gas escape: only " + gasAt(helper, GAUGE)
                        + " kg of 1.0 left at its node");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void readingMapsOntoTheBounds(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);
        fillGauge(helper, 300.0);

        helper.runAfterDelay(5, () -> {
            double pressure = gauge.getPressure();
            if (pressure <= 0) {
                helper.fail("A gauge full of gas reads no pressure");
                return;
            }

            gauge.setSettings(Mode.PRESSURE, 0.0, pressure / 2.0);
            if (gauge.getComparatorOutputSignal() != 15) {
                helper.fail("Above its upper bound the gauge should pin at 15, not "
                        + gauge.getComparatorOutputSignal());
                return;
            }

            gauge.setSettings(Mode.PRESSURE, pressure * 2.0, pressure * 3.0);
            if (gauge.getComparatorOutputSignal() != 0) {
                helper.fail("Below its lower bound the gauge should read 0, not "
                        + gauge.getComparatorOutputSignal());
                return;
            }

            // Bounds put the reading a quarter of the way along: 15 * 0.25 rounds to 4.
            gauge.setSettings(Mode.PRESSURE, pressure / 2.0, pressure * 2.5);
            if (gauge.getComparatorOutputSignal() != 4) {
                helper.fail("A quarter of the way along the range should read 4, not "
                        + gauge.getComparatorOutputSignal());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void temperatureModeReadsTemperature(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);
        fillGauge(helper, 900.0);

        helper.runAfterDelay(5, () -> {
            // 900 K over 0..1200 K is three quarters of the way along: 15 * 0.75 rounds to 11.
            gauge.setSettings(Mode.TEMPERATURE, 0.0, 1200.0);
            int signal = gauge.getComparatorOutputSignal();
            if (signal != 11) {
                helper.fail("Gas at " + gauge.getTemperature() + " K over 0..1200 K should read 11, not "
                        + signal);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void eachModeKeepsItsOwnBounds(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);

        gauge.setSettings(Mode.PRESSURE, 100.0, 200.0);
        gauge.setSettings(Mode.TEMPERATURE, 300.0, 400.0);

        if (gauge.getMode() != Mode.TEMPERATURE) {
            helper.fail("The gauge did not switch to temperature");
            return;
        }
        if (gauge.getBounds(Mode.PRESSURE).lower() != 100.0 || gauge.getBounds(Mode.PRESSURE).upper() != 200.0) {
            helper.fail("Setting temperature bounds disturbed the pressure bounds: "
                    + gauge.getBounds(Mode.PRESSURE));
            return;
        }
        if (gauge.getBounds(Mode.TEMPERATURE).lower() != 300.0 || gauge.getBounds(Mode.TEMPERATURE).upper() != 400.0) {
            helper.fail("The temperature bounds were not stored: " + gauge.getBounds(Mode.TEMPERATURE));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void boundsOutOfOrderAreRefused(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);
        gauge.setSettings(Mode.PRESSURE, 100.0, 200.0);

        if (gauge.setSettings(Mode.PRESSURE, 200.0, 100.0)) {
            helper.fail("Bounds in the wrong order were accepted");
            return;
        }
        if (gauge.setSettings(Mode.PRESSURE, 100.0, 100.0)) {
            helper.fail("An empty range was accepted");
            return;
        }
        if (gauge.setSettings(Mode.PRESSURE, -1.0, 100.0)) {
            helper.fail("A negative lower bound was accepted");
            return;
        }
        if (gauge.setSettings(Mode.PRESSURE, 0.0, Double.POSITIVE_INFINITY)) {
            helper.fail("An infinite upper bound was accepted");
            return;
        }
        if (gauge.getBounds().lower() != 100.0 || gauge.getBounds().upper() != 200.0) {
            helper.fail("Refused bounds still replaced the old ones: " + gauge.getBounds());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void comparatorFollowsTheGauge(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);
        placeComparator(helper);

        helper.runAfterDelay(COMPARATOR_SETTLE_TICKS, () -> {
            if (comparatorOutput(helper) != 0) {
                helper.fail("An empty gauge drove the comparator to " + comparatorOutput(helper));
                return;
            }

            fillGauge(helper, 300.0);
            helper.runAfterDelay(5, () -> {
                // Pin the reading at the top so the exact pressure does not matter.
                gauge.setSettings(Mode.PRESSURE, 0.0, gauge.getPressure() / 2.0);

                helper.runAfterDelay(COMPARATOR_SETTLE_TICKS, () -> {
                    if (comparatorOutput(helper) != 15) {
                        helper.fail("A gauge pinned at its upper bound drove the comparator to "
                                + comparatorOutput(helper) + ", not 15");
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gaugeTellsClientsWhatItReads(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);
        fillGauge(helper, 300.0);

        helper.runAfterDelay(40, () -> {
            if (gauge.getLastSentPressure() <= 0) {
                helper.fail("The gauge never told clients its pressure, so the needle would not move");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void gaugeStandsOnItsInletFace(GameTestHelper helper) {
        placeGauge(helper, Direction.UP);
        // Facing up, the plate is pressed against the floor: it must not float in the top of the block.
        if (helper.getBlockState(GAUGE).getShape(helper.getLevel(), helper.absolutePos(GAUGE)).max(Direction.Axis.Y)
                > GasGaugeBlock.PLATE_THICKNESS / 16.0 + 1e-6) {
            helper.fail("An upward-facing gauge's plate is not against the block below it");
            return;
        }
        if (!helper.getBlockState(GAUGE).hasProperty(BlockStateProperties.FACING)) {
            helper.fail("The gauge has no facing");
            return;
        }
        helper.succeed();
    }
}
