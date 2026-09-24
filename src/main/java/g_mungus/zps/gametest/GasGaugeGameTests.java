package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.GasGaugeBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity.Bounds;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity.Mode;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * The gas gauge: it joins the network on its inlet face and nowhere else, keeps what reaches it,
 * maps its reading onto the range of whichever property it is set to, and puts that on a
 * comparator. A sneaking, empty-handed click is what changes the property.
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
    public static void pressureReadsOverTheDuctsRange(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);
        fillGauge(helper, 300.0);

        helper.runAfterDelay(5, () -> {
            double pressure = gauge.getPressure();
            if (pressure <= 0) {
                helper.fail("A gauge full of gas reads no pressure");
                return;
            }
            if (gauge.getMode() != Mode.PRESSURE) {
                helper.fail("A new gauge should read pressure, not " + gauge.getMode());
                return;
            }
            int expected = (int) Math.round(15.0 * Math.min(1.0, pressure / GasGaugeBlock.MAX_PRESSURE));
            if (gauge.getComparatorOutputSignal() != expected) {
                helper.fail(pressure + " Pa over 0.." + GasGaugeBlock.MAX_PRESSURE + " Pa should read "
                        + expected + ", not " + gauge.getComparatorOutputSignal());
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
            // 900 K over 0..1478 K is 0.609 of the way along: 15 * 0.609 rounds to 9.
            gauge.setMode(Mode.TEMPERATURE);
            int signal = gauge.getComparatorOutputSignal();
            if (signal != 9) {
                helper.fail("Gas at " + gauge.getTemperature() + " K over 0..1478 K should read 9, not "
                        + signal);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void readingsPastTheRangePinAtItsEnds(GameTestHelper helper) {
        Bounds bounds = new Bounds(100.0, 200.0);
        if (bounds.fractionOf(50.0) != 0.0) {
            helper.fail("Below the range should pin at 0, not " + bounds.fractionOf(50.0));
            return;
        }
        if (bounds.fractionOf(500.0) != 1.0) {
            helper.fail("Above the range should pin at 1, not " + bounds.fractionOf(500.0));
            return;
        }
        if (bounds.fractionOf(125.0) != 0.25) {
            helper.fail("A quarter of the way along should be 0.25, not " + bounds.fractionOf(125.0));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void eachModeHasItsOwnRange(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);

        if (gauge.getBounds().upper() != GasGaugeBlock.MAX_PRESSURE) {
            helper.fail("Pressure should run to the duct's limit, not " + gauge.getBounds());
            return;
        }
        gauge.setMode(Mode.TEMPERATURE);
        if (gauge.getBounds().upper() != GasGaugeBlock.MAX_TEMPERATURE) {
            helper.fail("Temperature should run to the duct's limit, not " + gauge.getBounds());
            return;
        }
        helper.succeed();
    }

    private static InteractionResult click(GameTestHelper helper, boolean sneaking) {
        Player player = helper.makeMockSurvivalPlayer();
        player.setShiftKeyDown(sneaking);
        BlockPos pos = helper.absolutePos(GAUGE);
        return helper.getBlockState(GAUGE).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    @GameTest(template = TEMPLATE)
    public static void sneakingEmptyHandedClickCyclesTheMode(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);

        click(helper, true);
        if (gauge.getMode() != Mode.TEMPERATURE) {
            helper.fail("A sneaking click should move pressure on to temperature, not " + gauge.getMode());
            return;
        }
        click(helper, true);
        if (gauge.getMode() != Mode.PRESSURE) {
            helper.fail("A second sneaking click should come back round to pressure, not " + gauge.getMode());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void plainClickReadsWithoutChangingTheMode(GameTestHelper helper) {
        GasGaugeBlockEntity gauge = placeGauge(helper, Direction.UP);

        InteractionResult result = click(helper, false);
        if (gauge.getMode() != Mode.PRESSURE) {
            helper.fail("A click without sneaking changed the mode to " + gauge.getMode());
            return;
        }
        // Consumed, so whatever the player is holding is not used against the gauge as well.
        if (!result.consumesAction()) {
            helper.fail("A click without sneaking should be taken by the gauge; got " + result);
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

            // Temperature, because what 900 K reads is known; the pressure of a kilo of flux is not.
            fillGauge(helper, 900.0);
            gauge.setMode(Mode.TEMPERATURE);

            helper.runAfterDelay(COMPARATOR_SETTLE_TICKS, () -> {
                if (comparatorOutput(helper) != gauge.getComparatorOutputSignal()
                        || comparatorOutput(helper) == 0) {
                    helper.fail("A gauge reading " + gauge.getComparatorOutputSignal()
                            + " drove the comparator to " + comparatorOutput(helper));
                    return;
                }
                helper.succeed();
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
