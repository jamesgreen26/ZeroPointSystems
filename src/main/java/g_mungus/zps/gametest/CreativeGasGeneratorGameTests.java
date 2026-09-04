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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;

/**
 * The creative source: it fills its own node while its rate is above zero, stops when it is not,
 * offers a face on every side, and keeps its settings across a reload.
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

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gasReachesADuctOnAnyFace(GameTestHelper helper) {
        // Up, rather than a horizontal face: a single-outlet machine like the vent could never
        // offer it without being turned, so it proves the six-sided proposal, not a lucky default.
        BlockPos duct = GENERATOR.relative(Direction.UP);

        CreativeGasGeneratorBlockEntity generator = place(helper);
        helper.setBlock(duct, ModBlocks.GAS_DUCT.get().defaultBlockState());
        // Gently: a duct still bursts at its own ceiling, and a burst duct has no node to check.
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.005, 900.0);

        if (KelvinMod.INSTANCE.forceGetKelvin()
                .getEdgeBetween(node(helper, GENERATOR), node(helper, duct)) == null) {
            helper.fail("No edge was negotiated between the generator and the duct above it");
            return;
        }

        helper.runAfterDelay(60, () -> {
            if (gasAt(helper, duct) <= 0) {
                helper.fail("The generator is emitting but no gas reached the duct above it");
            }
            helper.succeed();
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
        generator.setSettings(ModGases.FLUX.getResourceLocation(), 0.375, 1234.0);

        CompoundTag tag = generator.saveWithoutMetadata(helper.getLevel().registryAccess());
        generator.loadWithComponents(tag, helper.getLevel().registryAccess());

        if (!generator.getGasId().equals(ModGases.FLUX.getResourceLocation())) {
            helper.fail("The chosen gas was lost on reload: " + generator.getGasId());
        }
        if (Math.abs(generator.getRate() - 0.375) > 1e-6) {
            helper.fail("The rate was lost on reload: " + generator.getRate());
        }
        if (Math.abs(generator.getEmissionTemperature() - 1234.0) > 1e-6) {
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
