package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.blockentity.gas.VentBlockEntity;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNodePos;

/**
 * The vent: it joins the network on its inlet face and nowhere else, empties whatever reaches its
 * node, tells clients enough to draw the jet, and shuts under redstone.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class VentGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    /** Long enough for gas to reach the vent and for several sync windows to close over it. */
    private static final int SETTLE_TICKS = 120;

    private static final BlockPos VENT = new BlockPos(3, 2, 3);
    /** North of the vent, so a vent facing south has its inlet pressed against it. */
    private static final BlockPos SOURCE = new BlockPos(3, 2, 2);

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

    private static void placeVent(GameTestHelper helper, Direction facing) {
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, facing));
    }

    /** A creative source north of the vent, running gently enough not to burst anything. */
    private static CreativeGasGeneratorBlockEntity placeSource(GameTestHelper helper, double rate) {
        helper.setBlock(SOURCE, ModBlocks.CREATIVE_GAS_GENERATOR.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(SOURCE);
        if (!(blockEntity instanceof CreativeGasGeneratorBlockEntity generator)) {
            helper.fail("The gas source has no block entity");
            throw new IllegalStateException();
        }
        generator.setSettings(ModGases.FLUX.getResourceLocation(), rate, 900.0);
        return generator;
    }

    private static boolean joinedToSource(GameTestHelper helper) {
        return KelvinMod.INSTANCE.forceGetKelvin()
                .getEdgeBetween(node(helper, SOURCE), node(helper, VENT)) != null;
    }

    @GameTest(template = TEMPLATE)
    public static void ventJoinsOnItsInlet(GameTestHelper helper) {
        // Venting south puts the inlet on the north face, which is the one the source is against.
        placeSource(helper, 0.0);
        placeVent(helper, Direction.SOUTH);

        if (!joinedToSource(helper)) {
            helper.fail("No edge formed between the source and the vent's inlet");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ventRefusesEveryFaceButItsInlet(GameTestHelper helper) {
        // Venting up puts the inlet underneath, so the north face the source is pressed against is
        // dead. The vent used to accept on all five faces that were not its outlet.
        placeSource(helper, 0.0);
        placeVent(helper, Direction.UP);

        if (joinedToSource(helper)) {
            helper.fail("The vent connected on a face that is not its inlet");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ventRefusesTheFaceItVentsFrom(GameTestHelper helper) {
        // Turned to vent straight at the source, that face is the outlet and stays closed.
        placeSource(helper, 0.0);
        placeVent(helper, Direction.NORTH);

        if (joinedToSource(helper)) {
            helper.fail("The vent connected on the face it vents from");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void ventReportsWhatItVentsSoTheJetCanBeDrawn(GameTestHelper helper) {
        placeSource(helper, 0.005);
        placeVent(helper, Direction.SOUTH);

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            if (!(helper.getBlockEntity(VENT) instanceof VentBlockEntity vent)) {
                helper.fail("Vent has no block entity");
                return;
            }
            if (vent.getTotalVented() <= 0) {
                helper.fail("Nothing reached the vent to vent");
                return;
            }
            // Assert against what actually went on the wire. The vent empties its node and then
            // syncs, so a sync that reports node mass always sends zero and the client draws no
            // particles at all — which is what it used to do. Checking the node's mass at an
            // arbitrary moment would not catch that, because gas is arriving every tick.
            if (vent.getLastSentMass() <= 0) {
                helper.fail("The vent vented " + vent.getTotalVented()
                        + " kg but told clients a rate of " + vent.getLastSentMass()
                        + ", so no jet would be drawn");
            }
            if (vent.getLastSentPressure() <= 0) {
                helper.fail("The vent reported zero pressure, so the jet would have no speed");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void redstoneShutsTheVent(GameTestHelper helper) {
        placeVent(helper, Direction.UP);
        helper.setBlock(VENT.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        if (!helper.getBlockState(VENT).getValue(VentBlock.POWERED)) {
            helper.fail("The vent did not notice the redstone beside it");
            return;
        }

        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, VENT), ModGases.FLUX, 1.0, 300.0);

        helper.runAfterDelay(20, () -> {
            if (gasAt(helper, VENT) < 1.0 - 1e-6) {
                helper.fail("A shut vent still vented: only " + gasAt(helper, VENT)
                        + " kg of 1.0 left at its node");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ventEmptiesItsNode(GameTestHelper helper) {
        placeVent(helper, Direction.UP);
        if (!(helper.getBlockEntity(VENT) instanceof VentBlockEntity)) {
            helper.fail("Vent has no block entity");
            return;
        }

        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, VENT), ModGases.FLUX, 1.0, 300.0);

        helper.runAfterDelay(5, () -> {
            if (gasAt(helper, VENT) > 1e-6) {
                helper.fail("The vent did not vent the gas at its node: "
                        + gasAt(helper, VENT) + " kg left");
            }
            helper.succeed();
        });
    }
}
