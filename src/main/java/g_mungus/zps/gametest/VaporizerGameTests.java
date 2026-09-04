package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.VaporizerBlock;
import g_mungus.zps.block.gas.VentBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.VentBlockEntity;
import g_mungus.zps.blockentity.gas.VaporizerBlockEntity;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;

/**
 * End-to-end cover for phase 1: the vaporizer makes Flux into its own buffer, the vent empties
 * whatever reaches it, and the two connect directly with no duct in between.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class VaporizerGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos VAPORIZER = new BlockPos(3, 2, 3);
    /** South of the vaporizer, so the vaporizer's outlet faces it. */
    private static final BlockPos VENT = new BlockPos(3, 2, 4);

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

    private static VaporizerBlockEntity placeVaporizer(GameTestHelper helper, Direction outlet) {
        helper.setBlock(VAPORIZER, ModBlocks.VAPORIZER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, outlet));
        BlockEntity blockEntity = helper.getBlockEntity(VAPORIZER);
        if (!(blockEntity instanceof VaporizerBlockEntity vaporizer)) {
            helper.fail("Vaporizer has no block entity");
            throw new IllegalStateException();
        }
        return vaporizer;
    }

    /** Fuel and power enough for one batch and then some. */
    private static void supply(VaporizerBlockEntity vaporizer) {
        // receiveEnergy is capped at the storage's per-tick transfer rate, so fill it the way a
        // cable network would: repeatedly, until it stops accepting.
        var energy = vaporizer.getEnergyStorage(null);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
            // keep going until full
        }
        vaporizer.getInventory().insertItem(VaporizerBlockEntity.ICE_SLOT,
                new ItemStack(Items.BLUE_ICE, 4), false);
        vaporizer.getInventory().insertItem(VaporizerBlockEntity.LITHIUM_SLOT,
                new ItemStack(ModItems.LITHIUM_INGOT.get(), 4), false);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void vaporizerProducesFluxIntoItsBuffer(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = placeVaporizer(helper, Direction.UP);
        supply(vaporizer);

        // One batch takes the recipe's processTime; give it a little margin.
        helper.runAfterDelay(VaporizerBlockEntity.DEFAULT_PROCESS_TICKS + 20, () -> {
            if (gasAt(helper, VAPORIZER) <= 0) {
                helper.fail("The vaporizer produced no Flux after a full batch");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void vaporizerRegistersATankNodeWithItsOwnVolume(GameTestHelper helper) {
        placeVaporizer(helper, Direction.UP);

        DuctNode node = KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, VAPORIZER));
        if (node == null) {
            helper.fail("The vaporizer registered no node at all");
            return;
        }
        // Kelvin's INodeBlock default hands out a plain pipe node; the vaporizer must override it,
        // or its "buffer" would be a duct-sized volume with the wrong behaviour.
        if (node.getBehavior() != NodeBehaviorType.TANK) {
            helper.fail("Expected a TANK node, got " + node.getBehavior());
        }
        if (Math.abs(node.getVolume() - VaporizerBlock.VOLUME) > 1e-6) {
            helper.fail("Expected the vaporizer's own volume " + VaporizerBlock.VOLUME
                    + ", got " + node.getVolume());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void producedFluxLandsInTheBufferAsMassAndPressure(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = placeVaporizer(helper, Direction.UP);
        supply(vaporizer);

        helper.runAfterDelay(VaporizerBlockEntity.DEFAULT_PROCESS_TICKS + 20, () -> {
            // The mass the shipped recipe promises.
            double mass = gasAt(helper, VAPORIZER);
            if (Math.abs(mass - 0.5) > 1e-6) {
                helper.fail("Expected 0.5 kg of Flux in the buffer, found " + mass);
            }

            // It must be Flux specifically, not just any gas.
            Double flux = KelvinMod.INSTANCE.forceGetKelvin()
                    .getGasMassAt(node(helper, VAPORIZER)).get(ModGases.FLUX);
            if (flux == null || flux <= 0) {
                helper.fail("The buffer holds gas, but none of it is Flux");
            }

            // Mass alone is not enough: the solver drives everything off pressure, and the GUI
            // gauge reads it.
            double pressure = KelvinMod.INSTANCE.forceGetKelvin().getPressureAt(node(helper, VAPORIZER));
            if (pressure <= 0) {
                helper.fail("Flux is in the buffer but the node has no pressure: " + pressure);
            }

            // Not just non-zero: the buffer must be sized so a batch visibly moves the gauge.
            // With Kelvin's default pipe ceiling this was 2 pixels out of 50, which reads as
            // nothing happening at all.
            double fraction = vaporizer.bufferFraction();
            if (fraction <= 0) {
                helper.fail("The buffer gauge would read empty despite holding Flux");
            } else if (fraction < 0.1) {
                helper.fail(String.format(
                        "One batch moves the buffer gauge only %.1f%% (%d of 50 pixels) — the"
                                + " buffer is oversized for what the machine produces",
                        fraction * 100, Math.round(fraction * 50)));
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void vaporizerStallsRatherThanOverfillingItsBuffer(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = placeVaporizer(helper, Direction.UP);

        // Pre-charge into the stall band instead of waiting for several real batches: above the
        // 90% threshold where the machine stops, but below the ceiling — Kelvin ruptures a node
        // whose pressure exceeds maxPressure, explosion and all, which would destroy the block
        // under test.
        KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, VAPORIZER), ModGases.FLUX, 2.0, 900.0);
        double before = gasAt(helper, VAPORIZER);
        supply(vaporizer);

        helper.runAfterDelay(40, () -> {
            if (KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, VAPORIZER)) == null) {
                helper.fail("The vaporizer ruptured: the test overfilled it past its ceiling");
                return;
            }
            if (vaporizer.bufferFraction() < 0.9) {
                helper.fail("Test setup did not reach the stall threshold: "
                        + vaporizer.bufferFraction());
                return;
            }
            // Powered and fed, but too full to run: it must not even start a batch.
            if (vaporizer.getProgress() != 0) {
                helper.fail("A full vaporizer started a batch anyway, progress=" + vaporizer.getProgress());
            }
            if (gasAt(helper, VAPORIZER) > before + 1e-6) {
                helper.fail("A full vaporizer kept producing: " + before + " kg -> "
                        + gasAt(helper, VAPORIZER) + " kg");
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void addingGasToTheVaporizerNodeSucceeds(GameTestHelper helper) {
        placeVaporizer(helper, Direction.UP);

        // addGasAtTemperature returns false when the node is missing, and the machine ignores that
        // return value — so check the contract directly.
        boolean added = KelvinMod.INSTANCE.forceGetKelvin()
                .addGasAtTemperature(node(helper, VAPORIZER), ModGases.FLUX, 0.25, 900.0);
        if (!added) {
            helper.fail("Kelvin refused gas for the vaporizer's node — the node is missing");
        }
        if (Math.abs(gasAt(helper, VAPORIZER) - 0.25) > 1e-6) {
            helper.fail("Gas was accepted but did not land in the buffer: " + gasAt(helper, VAPORIZER));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void vaporizerConnectsDirectlyToVent(GameTestHelper helper) {
        // The case Clockwork's duct-authored design cannot express: two machines, no duct.
        placeVaporizer(helper, Direction.SOUTH);
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));

        if (KelvinMod.INSTANCE.forceGetKelvin()
                .getEdgeBetween(node(helper, VAPORIZER), node(helper, VENT)) == null) {
            helper.fail("No edge formed between the vaporizer outlet and the vent inlet");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void ventReportsWhatItVentsSoTheJetCanBeDrawn(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = placeVaporizer(helper, Direction.SOUTH);
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        supply(vaporizer);

        helper.runAfterDelay(VaporizerBlockEntity.DEFAULT_PROCESS_TICKS + 60, () -> {
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

    @GameTest(template = TEMPLATE)
    public static void vaporizerRefusesConnectionsAwayFromItsOutlet(GameTestHelper helper) {
        // Outlet points north, vent sits south, so nothing should join up.
        placeVaporizer(helper, Direction.NORTH);
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));

        if (KelvinMod.INSTANCE.forceGetKelvin()
                .getEdgeBetween(node(helper, VAPORIZER), node(helper, VENT)) != null) {
            helper.fail("The vaporizer connected on a face that is not its outlet");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ventJoinsOnItsInletAlone(GameTestHelper helper) {
        // The vent points up, so its inlet is the face underneath — not the north face the
        // vaporizer's outlet is pressed against, live though that face used to be.
        placeVaporizer(helper, Direction.SOUTH);
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.UP));

        if (KelvinMod.INSTANCE.forceGetKelvin()
                .getEdgeBetween(node(helper, VAPORIZER), node(helper, VENT)) != null) {
            helper.fail("The vent connected on a face that is not its inlet");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ventRefusesTheFaceItVentsFrom(GameTestHelper helper) {
        // Turned to vent straight at the vaporizer, that face is the outlet and stays closed.
        placeVaporizer(helper, Direction.SOUTH);
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));

        if (KelvinMod.INSTANCE.forceGetKelvin()
                .getEdgeBetween(node(helper, VAPORIZER), node(helper, VENT)) != null) {
            helper.fail("The vent connected on the face it vents from");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void redstoneShutsTheVent(GameTestHelper helper) {
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.UP));
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
        helper.setBlock(VENT, ModBlocks.VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.UP));
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
