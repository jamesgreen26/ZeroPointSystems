package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;

/**
 * The creative source: it fills its own node while its rate is above zero, stops when it is not,
 * offers a face on every horizontal side, shuts off short of a duct's ceiling, and keeps its
 * settings across a reload.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreativeGasGeneratorGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos GENERATOR = new BlockPos(3, 2, 3);

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

    private static CreativeGasGeneratorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(GENERATOR, ModBlocks.CREATIVE_GAS_GENERATOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(GENERATOR);
        if (!(blockEntity instanceof CreativeGasGeneratorBlockEntity generator)) {
            helper.fail("Creative Gas Generator has no block entity");
            throw new IllegalStateException();
        }
        return generator;
    }

    @GameTest(template = TEMPLATE)
    public static void registersATankNodeWithItsOwnVolume(GameTestHelper helper) {
        place(helper);

        DuctNode node = KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, GENERATOR));
        if (node == null) {
            helper.fail("The generator registered no node at all");
            return;
        }
        if (node.getBehavior() != NodeBehaviorType.TANK) {
            helper.fail("Expected a TANK node, got " + node.getBehavior());
        }
        // No ceiling, or a creative source left running would rupture its own block.
        if (node.getMaxPressure() < 1e30) {
            helper.fail("The generator's node has a pressure ceiling it can burst at: "
                    + node.getMaxPressure());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void emitsTheChosenGasIntoItsOwnNode(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.05, 900.0);

        helper.runAfterDelay(20, () -> {
            Double flux = KelvinMod.INSTANCE.forceGetKelvin()
                    .getGasMassAt(node(helper, GENERATOR)).get(ModGases.FLUX);
            if (flux == null || flux <= 0) {
                helper.fail("A running generator produced no Flux at all");
                return;
            }
            // Twenty ticks' worth, allowing for the tick the settings landed on and a little slack.
            if (flux < 0.05 * 10) {
                helper.fail("The generator is emitting far below its set rate: " + flux + " kg");
            }
            helper.succeed();
        });
    }

    /** A rate of zero is the block's only off switch, so it has to actually switch it off. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aZeroRateEmitsNothing(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.0, 900.0);

        helper.runAfterDelay(20, () -> {
            if (gasAt(helper, GENERATOR) > 1e-6) {
                helper.fail("A generator set to zero still emitted " + gasAt(helper, GENERATOR) + " kg");
            }
            helper.succeed();
        });
    }

    /** It joins a duct on each of its four sides, and never on the top or bottom, where the fan is. */
    @GameTest(template = TEMPLATE)
    public static void connectsOnItsHorizontalSidesOnly(GameTestHelper helper) {
        place(helper);
        for (Direction direction : Direction.values()) {
            BlockPos duct = GENERATOR.relative(direction);
            helper.setBlock(duct, ModBlocks.GAS_DUCT.get().defaultBlockState());
            boolean joined = KelvinMod.INSTANCE.forceGetKelvin()
                    .getEdgeBetween(node(helper, GENERATOR), node(helper, duct)) != null;
            helper.assertTrue(joined == direction.getAxis().isHorizontal(),
                    "Duct " + direction + " of the generator " + (joined ? "joined" : "did not join"));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gasReachesADuctOnItsSide(GameTestHelper helper) {
        BlockPos duct = GENERATOR.relative(Direction.EAST);

        CreativeGasGeneratorBlockEntity generator = place(helper);
        helper.setBlock(duct, ModBlocks.GAS_DUCT.get().defaultBlockState());
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.005, 900.0);

        helper.runAfterDelay(60, () -> {
            if (gasAt(helper, duct) <= 0) {
                helper.fail("The generator is emitting but no gas reached the duct beside it");
            }
            helper.succeed();
        });
    }

    /**
     * Left running into a dead end, it fills right up to the cutoff and stops there, so its own
     * output can never burst a duct; once gas is let out it picks up again.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void shutsOffShortOfADuctsCeiling(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        DuctNodePos nodePos = node(helper, GENERATOR);
        var kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.5, 300.0);
        double[] massWhenFull = new double[1];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    double pressure = kelvin.getPressureAt(nodePos);
                    helper.assertTrue(pressure <= CreativeGasGeneratorBlockEntity.PRESSURE_LIMIT * 1.005,
                            "Pressure overshot the cutoff: " + pressure + " Pa");
                    helper.assertTrue(pressure > CreativeGasGeneratorBlockEntity.PRESSURE_LIMIT * 0.99,
                            "Still filling at " + pressure + " Pa");
                })
                .thenIdle(5)
                .thenExecute(() -> massWhenFull[0] = gasAt(helper, GENERATOR))
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(Math.abs(gasAt(helper, GENERATOR) - massWhenFull[0]) < 1e-6,
                        "A full generator should emit nothing, but went from " + massWhenFull[0]
                                + " to " + gasAt(helper, GENERATOR) + " kg"))
                .thenExecute(() -> kelvin.removeGas(nodePos, ModGases.FLUX, massWhenFull[0] / 2))
                .thenWaitUntil(() -> helper.assertTrue(gasAt(helper, GENERATOR) > massWhenFull[0] / 2 + 1e-6,
                        "It should pick up again once the pressure drops"))
                .thenSucceed();
    }

    /**
     * At a rate where one tick's worth alone would overshoot the cutoff, it still fills: it emits
     * only what fits, so it ends right at the limit instead of never emitting at all.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aRateThatWouldOvershootStillFillsToTheCutoff(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        DuctNodePos nodePos = node(helper, GENERATOR);
        generator.setSettings(ModGases.FLUX.getResourceLocation(), CreativeGasGeneratorBlockEntity.MAX_RATE, 300.0);

        helper.succeedWhen(() -> {
            double pressure = KelvinMod.INSTANCE.forceGetKelvin().getPressureAt(nodePos);
            helper.assertTrue(pressure <= CreativeGasGeneratorBlockEntity.PRESSURE_LIMIT * 1.005,
                    "Pressure overshot the cutoff: " + pressure + " Pa");
            helper.assertTrue(pressure > CreativeGasGeneratorBlockEntity.PRESSURE_LIMIT * 0.99,
                    "Only reached " + pressure + " Pa of " + CreativeGasGeneratorBlockEntity.PRESSURE_LIMIT);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void theGeneratorDoesNotRuptureUnderItsOwnOutput(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        // Far past what would burst a duct, into a node with nowhere to put it.
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 1.0, 900.0);

        helper.runAfterDelay(40, () -> {
            if (helper.getBlockState(GENERATOR).getBlock() != ModBlocks.CREATIVE_GAS_GENERATOR.get()) {
                helper.fail("The generator blew itself up with its own output");
                return;
            }
            if (KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, GENERATOR)) == null) {
                helper.fail("The generator's node was ruptured by its own output");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void settingsSurviveAReload(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.375, 789.0);

        CompoundTag tag = generator.saveWithoutMetadata();
        generator.load(tag);

        if (!generator.getGasId().equals(ModGases.FLUX.getResourceLocation())) {
            helper.fail("The chosen gas was lost on reload: " + generator.getGasId());
        }
        if (Math.abs(generator.getRate() - 0.375) > 1e-6) {
            helper.fail("The rate was lost on reload: " + generator.getRate());
        }
        if (Math.abs(generator.getEmissionTemperature() - 789.0) > 1e-6) {
            helper.fail("The temperature was lost on reload: " + generator.getEmissionTemperature());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnknownGasIsRejectedRatherThanStored(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        generator.setSettings(ZPSMod.resource("not_a_gas"), 0.5, 500.0);

        if (generator.getGasId().equals(ZPSMod.resource("not_a_gas"))) {
            helper.fail("The generator accepted a gas that is not registered");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void settingsAreClampedToTheBlocksRange(GameTestHelper helper) {
        CreativeGasGeneratorBlockEntity generator = place(helper);
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 1e9, 1e9);

        if (generator.getRate() > CreativeGasGeneratorBlockEntity.MAX_RATE) {
            helper.fail("The rate was not clamped: " + generator.getRate());
        }
        if (generator.getEmissionTemperature() > CreativeGasGeneratorBlockEntity.MAX_TEMPERATURE) {
            helper.fail("The temperature was not clamped: " + generator.getEmissionTemperature());
        }
        helper.succeed();
    }
}
