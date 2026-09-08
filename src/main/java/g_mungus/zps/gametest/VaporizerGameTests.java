package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.OneWayCompositeDuctEdge;
import g_mungus.zps.blockentity.gas.VaporizerBlockEntity;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;

/**
 * The Vaporizer against its test recipe — blue ice and a lithium ingot to 0.75 kg of Steam and
 * 0.25 kg of Flux, needing 375 K and costing 100 K: it heats on FE only when a recipe is waiting, vaporizes once
 * hot enough, pays the cost afterwards, and keeps its state across a reload.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class VaporizerGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos VAPORIZER = new BlockPos(3, 2, 3);
    private static final int SEED_COUNT = 4;

    /** The test recipe's figures, as registered in data. */
    private static final double RECIPE_MIN_TEMPERATURE = 375.0;
    private static final double RECIPE_COST = 100.0;
    private static final double RECIPE_STEAM_KG = 0.9;
    private static final double RECIPE_FLUX_KG = 0.1;

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static double gasAt(GameTestHelper helper, BlockPos relative, GasType gas) {
        Double mass = KelvinMod.INSTANCE.forceGetKelvin().getGasMassAt(node(helper, relative)).get(gas);
        return mass == null ? 0 : mass;
    }

    private static double fluxAt(GameTestHelper helper, BlockPos relative) {
        return gasAt(helper, relative, ModGases.FLUX);
    }

    /** The heat one craft's worth of gas carries at {@code temperature}, by Kelvin's bare-gas cv. */
    private static double heatOfOneCraftAt(double temperature) {
        return (RECIPE_FLUX_KG * bareGasCv(ModGases.FLUX) + RECIPE_STEAM_KG * bareGasCv(ModGases.STEAM)) * temperature;
    }

    private static double bareGasCv(GasType gas) {
        return gas.getSpecificHeatCapacity() * 1000.0 / gas.getAdiabaticIndex();
    }

    private static VaporizerBlockEntity place(GameTestHelper helper) {
        helper.setBlock(VAPORIZER, ModBlocks.VAPORIZER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(VAPORIZER);
        if (!(blockEntity instanceof VaporizerBlockEntity vaporizer)) {
            helper.fail("Vaporizer has no block entity");
            throw new IllegalStateException();
        }
        return vaporizer;
    }

    private static IItemHandlerModifiable rawInventory(VaporizerBlockEntity vaporizer) {
        return (IItemHandlerModifiable) vaporizer.getMenuInventory();
    }

    private static void fillEnergy(VaporizerBlockEntity vaporizer) {
        IEnergyStorage energy = vaporizer.getEnergyStorage(null);
        // Receive is capped per call, not per tick, so keep pushing until it is full.
        for (int i = 0; i < 64 && energy.getEnergyStored() < energy.getMaxEnergyStored(); i++) {
            energy.receiveEnergy(energy.getMaxEnergyStored(), false);
        }
    }

    private static void loadTestRecipe(VaporizerBlockEntity vaporizer) {
        IItemHandlerModifiable raw = rawInventory(vaporizer);
        raw.setStackInSlot(0, new ItemStack(Items.BLUE_ICE, SEED_COUNT));
        raw.setStackInSlot(1, new ItemStack(ModItems.LITHIUM_INGOT.get(), SEED_COUNT));
    }

    @GameTest(template = TEMPLATE)
    public static void registersATankNode(GameTestHelper helper) {
        place(helper);

        DuctNode node = KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(node(helper, VAPORIZER));
        if (node == null) {
            helper.fail("The vaporizer registered no node at all");
            return;
        }
        if (node.getBehavior() != NodeBehaviorType.TANK) {
            helper.fail("Expected a TANK node, got " + node.getBehavior());
        }
        helper.succeed();
    }

    /** Every connection is a check valve pointing out, so nothing on the line can flow back in. */
    @GameTest(template = TEMPLATE)
    public static void connectionsOnlyCarryGasOutward(GameTestHelper helper) {
        place(helper);
        for (Direction direction : Direction.values()) {
            BlockPos ductPos = VAPORIZER.relative(direction);
            helper.setBlock(ductPos, ModBlocks.GAS_DUCT.get().defaultBlockState());

            DuctNodePos vaporizer = node(helper, VAPORIZER);
            DuctNodePos duct = node(helper, ductPos);
            DuctEdge edge = KelvinMod.INSTANCE.forceGetKelvin().getEdgeBetween(vaporizer, duct);
            if (edge == null) {
                helper.fail("No edge was negotiated to the duct " + direction + " of the vaporizer");
                return;
            }
            if (!(edge instanceof OneWayCompositeDuctEdge oneWay)) {
                helper.fail("The edge " + direction + " of the vaporizer is not one-way: " + edge);
                return;
            }
            // Flow is permitted from nodeA to nodeB unless reversed; either way it must start at
            // the vaporizer.
            DuctNodePos source = oneWay.getReversed() ? edge.getNodeB() : edge.getNodeA();
            if (!source.equals(vaporizer)) {
                helper.fail("The edge " + direction + " of the vaporizer lets gas flow back into it");
                return;
            }
        }
        helper.succeed();
    }

    /** With power and a matching recipe, the machine heats to the recipe's floor and vaporizes. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void heatsThenVaporizesTheTestRecipe(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = place(helper);
        fillEnergy(vaporizer);
        int energyBefore = vaporizer.getEnergyStorage(null).getEnergyStored();
        loadTestRecipe(vaporizer);

        helper.succeedWhen(() -> {
            double flux = fluxAt(helper, VAPORIZER);
            if (flux < RECIPE_FLUX_KG - 1e-6) {
                helper.fail("No Flux vaporized yet: " + flux + " kg");
            }
            double steam = gasAt(helper, VAPORIZER, ModGases.STEAM);
            if (steam < RECIPE_STEAM_KG - 1e-6) {
                helper.fail("Flux appeared without its Steam: " + steam + " kg");
            }
            IItemHandlerModifiable raw = rawInventory(vaporizer);
            if (raw.getStackInSlot(0).getCount() >= SEED_COUNT || raw.getStackInSlot(1).getCount() >= SEED_COUNT) {
                helper.fail("Flux appeared but no ingredients were consumed");
            }
            if (vaporizer.getEnergyStorage(null).getEnergyStored() >= energyBefore) {
                helper.fail("Flux appeared but no FE was spent heating");
            }
        });
    }

    /** The gas is emitted at the temperature the machine reached, and only then is the cost paid. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gasComesOutHotAndTheMachineCoolsByTheCost(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = place(helper);
        // Already hot enough for exactly one craft, and no power to reheat afterwards.
        vaporizer.setMachineTemperature(RECIPE_MIN_TEMPERATURE);
        loadTestRecipe(vaporizer);

        helper.succeedWhen(() -> {
            double flux = fluxAt(helper, VAPORIZER);
            if (flux < RECIPE_FLUX_KG - 1e-6) {
                helper.fail("No Flux vaporized yet: " + flux + " kg");
            }
            double expected = RECIPE_MIN_TEMPERATURE - RECIPE_COST;
            if (Math.abs(vaporizer.getMachineTemperature() - expected) > 1e-6) {
                helper.fail("Expected the machine at " + expected + " K after paying the cost, got "
                        + vaporizer.getMachineTemperature());
            }
            // Gas made at 375 K carries far more heat than the same gas at 275 K would; the node's
            // energy tells which temperature the machine used.
            double heat = KelvinMod.INSTANCE.forceGetKelvin().getHeatEnergy(node(helper, VAPORIZER));
            double ifPaidFirst = heatOfOneCraftAt(expected);
            double ifReadFirst = heatOfOneCraftAt(RECIPE_MIN_TEMPERATURE);
            if (heat < (ifPaidFirst + ifReadFirst) / 2) {
                helper.fail("The gas was emitted cold: node holds " + heat + " J, expected about " + ifReadFirst);
            }
        });
    }

    /** No FE, no heat: the machine sits at ambient and reports that it is starved. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void doesNotHeatWithoutPower(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = place(helper);
        loadTestRecipe(vaporizer);

        helper.runAfterDelay(40, () -> {
            if (Math.abs(vaporizer.getMachineTemperature() - VaporizerBlockEntity.AMBIENT_TEMPERATURE_K) > 1e-6) {
                helper.fail("An unpowered vaporizer changed temperature: " + vaporizer.getMachineTemperature());
            }
            if (fluxAt(helper, VAPORIZER) > 0) {
                helper.fail("An unpowered vaporizer produced gas");
            }
            if (vaporizer.getStatus() != VaporizerBlockEntity.Status.NO_POWER) {
                helper.fail("Expected NO_POWER, got " + vaporizer.getStatus());
            }
            helper.succeed();
        });
    }

    /** Power is only spent when there is a recipe to heat for. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void doesNotHeatWithoutARecipe(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = place(helper);
        fillEnergy(vaporizer);
        int energyBefore = vaporizer.getEnergyStorage(null).getEnergyStored();
        // Half the recipe: lithium alone matches nothing.
        rawInventory(vaporizer).setStackInSlot(0, new ItemStack(ModItems.LITHIUM_INGOT.get(), SEED_COUNT));

        helper.runAfterDelay(40, () -> {
            if (vaporizer.getEnergyStorage(null).getEnergyStored() != energyBefore) {
                helper.fail("FE was spent with no recipe to run");
            }
            if (Math.abs(vaporizer.getMachineTemperature() - VaporizerBlockEntity.AMBIENT_TEMPERATURE_K) > 1e-6) {
                helper.fail("The machine heated with no recipe to run: " + vaporizer.getMachineTemperature());
            }
            if (vaporizer.getStatus() != VaporizerBlockEntity.Status.IDLE) {
                helper.fail("Expected IDLE, got " + vaporizer.getStatus());
            }
            helper.succeed();
        });
    }

    /** Hoppers and pipes may feed the slots but never pull ingredients back out. */
    @GameTest(template = TEMPLATE)
    public static void automationCannotExtractIngredients(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = place(helper);
        loadTestRecipe(vaporizer);

        IItemHandler automation = vaporizer.getItemHandler(null);
        for (int slot = 0; slot < automation.getSlots(); slot++) {
            ItemStack extracted = automation.extractItem(slot, 64, false);
            if (!extracted.isEmpty()) {
                helper.fail("Automation extracted " + extracted + " from slot " + slot);
            }
        }
        if (rawInventory(vaporizer).getStackInSlot(0).getCount() != SEED_COUNT) {
            helper.fail("The ingredient count changed under a blocked extraction");
        }
        // The GUI handler stays open so players can take their items back.
        ItemStack reclaimed = vaporizer.getMenuInventory().extractItem(0, 64, false);
        if (reclaimed.getCount() != SEED_COUNT) {
            helper.fail("The menu could not reclaim the ingredient: " + reclaimed);
        }
        helper.succeed();
    }

    /** Only items some vaporizing recipe uses may go in. */
    @GameTest(template = TEMPLATE)
    public static void slotsRejectItemsNoRecipeUses(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = place(helper);
        IItemHandler automation = vaporizer.getItemHandler(null);

        ItemStack cobblestone = new ItemStack(Items.COBBLESTONE, SEED_COUNT);
        if (automation.insertItem(0, cobblestone, false).getCount() != SEED_COUNT) {
            helper.fail("A slot accepted cobblestone, which no recipe vaporizes");
        }
        ItemStack blueIce = new ItemStack(Items.BLUE_ICE, SEED_COUNT);
        if (!automation.insertItem(0, blueIce, false).isEmpty()) {
            helper.fail("A slot refused blue ice, which the test recipe vaporizes");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stateSurvivesAReload(GameTestHelper helper) {
        VaporizerBlockEntity vaporizer = place(helper);
        fillEnergy(vaporizer);
        vaporizer.setMachineTemperature(321.5);
        loadTestRecipe(vaporizer);
        int energy = vaporizer.getEnergyStorage(null).getEnergyStored();

        CompoundTag tag = vaporizer.saveWithoutMetadata(helper.getLevel().registryAccess());
        rawInventory(vaporizer).setStackInSlot(0, ItemStack.EMPTY);
        vaporizer.setMachineTemperature(VaporizerBlockEntity.AMBIENT_TEMPERATURE_K);
        vaporizer.loadWithComponents(tag, helper.getLevel().registryAccess());

        if (Math.abs(vaporizer.getMachineTemperature() - 321.5) > 1e-6) {
            helper.fail("The temperature was lost on reload: " + vaporizer.getMachineTemperature());
        }
        if (vaporizer.getEnergyStorage(null).getEnergyStored() != energy) {
            helper.fail("The energy was lost on reload: " + vaporizer.getEnergyStorage(null).getEnergyStored());
        }
        if (rawInventory(vaporizer).getStackInSlot(0).getCount() != SEED_COUNT) {
            helper.fail("The inventory was lost on reload: " + rawInventory(vaporizer).getStackInSlot(0));
        }
        helper.succeed();
    }
}
