package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.CryoCondenserBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.block.gas.core.OneWayCompositeDuctEdge;
import g_mungus.zps.blockentity.gas.CryoCondenserBlockEntity;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.recipe.CondensingRecipe;
import g_mungus.zps.recipe.GasOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctEdge;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.NodeBehaviorType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.List;

/**
 * The Cryo Condenser against its data recipe — 1 kg of Steam to one ice, at 250 K or below, costing
 * 100 K for Steam at 373 K: it cools on FE only when a recipe's gas is waiting, freezes once cold
 * enough, pays more for hotter gas, skips a recipe whose product has nowhere to go, and lets gas
 * flow back out of its port.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class CryoCondenserGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos CONDENSER = new BlockPos(3, 2, 3);
    private static final BlockPos LEFT = new BlockPos(1, 2, 3);
    private static final BlockPos RIGHT = new BlockPos(5, 2, 3);

    /** The data recipe's figures. */
    private static final double RECIPE_MAX_TEMPERATURE = 250.0;
    private static final double RECIPE_STEAM_KG = 1.0;

    private static DuctNodePos node(GameTestHelper helper, BlockPos relative) {
        return GasEdgeNegotiator.nodePos(helper.getLevel(), helper.absolutePos(relative));
    }

    private static double gasAt(GameTestHelper helper, BlockPos relative, GasType gas) {
        Double mass = KelvinMod.INSTANCE.forceGetKelvin().getGasMassAt(node(helper, relative)).get(gas);
        return mass == null ? 0 : mass;
    }

    private static void addGas(GameTestHelper helper, BlockPos relative, GasType gas, double kg, double temperatureK) {
        KelvinMod.INSTANCE.forceGetKelvin().addGasAtTemperature(node(helper, relative), gas, kg, temperatureK);
    }

    private static GasType air() {
        return GasTypeRegistry.INSTANCE.getGasType(ResourceLocation.fromNamespaceAndPath("kelvin", "air"));
    }

    private static CryoCondenserBlockEntity place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, ModBlocks.CRYO_CONDENSER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(at);
        if (!(blockEntity instanceof CryoCondenserBlockEntity condenser)) {
            helper.fail("Cryo Condenser has no block entity");
            throw new IllegalStateException();
        }
        return condenser;
    }

    private static IEnergyStorage energy(CryoCondenserBlockEntity condenser) {
        return condenser.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    }

    private static void fillEnergy(CryoCondenserBlockEntity condenser) {
        IEnergyStorage energy = energy(condenser);
        // Receive is capped per call, not per tick, so keep pushing until it is full.
        for (int i = 0; i < 64 && energy.getEnergyStored() < energy.getMaxEnergyStored(); i++) {
            energy.receiveEnergy(energy.getMaxEnergyStored(), false);
        }
    }

    private static int countOf(CryoCondenserBlockEntity condenser, net.minecraft.world.item.Item item) {
        IItemHandler inventory = condenser.getMenuInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @GameTest(template = TEMPLATE)
    public static void registersATankNodeWithAnOpenPortOnTop(GameTestHelper helper) {
        place(helper, CONDENSER);
        DuctNodePos condenser = node(helper, CONDENSER);

        DuctNode node = KelvinMod.INSTANCE.forceGetKelvin().getNodeAt(condenser);
        if (node == null || node.getBehavior() != NodeBehaviorType.TANK) {
            helper.fail("Expected a TANK node, got " + (node == null ? "none" : node.getBehavior()));
            return;
        }

        for (Direction direction : Direction.values()) {
            BlockPos ductPos = CONDENSER.relative(direction);
            helper.setBlock(ductPos, ModBlocks.GAS_DUCT.get().defaultBlockState());
            DuctEdge edge = KelvinMod.INSTANCE.forceGetKelvin().getEdgeBetween(condenser, node(helper, ductPos));
            if (direction != CryoCondenserBlock.PORT) {
                if (edge != null) {
                    helper.fail("The condenser connected to a duct " + direction + " of it; only the top should join");
                    return;
                }
                continue;
            }
            if (edge == null) {
                helper.fail("No edge was negotiated to the duct on top of the condenser");
                return;
            }
            if (edge instanceof OneWayCompositeDuctEdge) {
                helper.fail("The port is one-way; gas the condenser cannot freeze would get stuck inside");
                return;
            }
        }
        helper.succeed();
    }

    /** With nothing to freeze, it spends nothing cooling. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void staysIdleWithoutGas(GameTestHelper helper) {
        CryoCondenserBlockEntity condenser = place(helper, CONDENSER);
        fillEnergy(condenser);
        int energyBefore = energy(condenser).getEnergyStored();

        helper.runAfterDelay(20, () -> {
            if (energy(condenser).getEnergyStored() != energyBefore) {
                helper.fail("Spent FE with no gas to freeze");
            }
            if (condenser.getStatus() != CryoCondenserBlockEntity.Status.IDLE) {
                helper.fail("Expected IDLE, got " + condenser.getStatus());
            }
            helper.succeed();
        });
    }

    /** With power and Steam at its node, it cools to the recipe's ceiling and freezes ice. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void coolsThenFreezesSteamIntoIce(GameTestHelper helper) {
        CryoCondenserBlockEntity condenser = place(helper, CONDENSER);
        fillEnergy(condenser);
        int energyBefore = energy(condenser).getEnergyStored();
        double steamBefore = 3.0;
        addGas(helper, CONDENSER, ModGases.STEAM, steamBefore, 300.0);

        helper.succeedWhen(() -> {
            if (countOf(condenser, Items.ICE) < 1) {
                helper.fail("No ice frozen yet");
            }
            double steam = gasAt(helper, CONDENSER, ModGases.STEAM);
            if (steam > steamBefore - RECIPE_STEAM_KG + 1e-6) {
                helper.fail("Ice appeared but the Steam was not taken: " + steam + " kg left");
            }
            if (energy(condenser).getEnergyStored() >= energyBefore) {
                helper.fail("Ice appeared but no FE was spent cooling");
            }
        });
    }

    /** Hot Steam warms the machine more per craft than cold Steam does. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void hotterGasWarmsTheMachineMore(GameTestHelper helper) {
        CryoCondenserBlockEntity hot = place(helper, LEFT);
        CryoCondenserBlockEntity cold = place(helper, RIGHT);
        // Too warm to craft, with no power to cool, while Kelvin settles the gas into each node.
        hot.setMachineTemperature(RECIPE_MAX_TEMPERATURE + 50);
        cold.setMachineTemperature(RECIPE_MAX_TEMPERATURE + 50);
        addGas(helper, LEFT, ModGases.STEAM, 1.5, 600.0);
        addGas(helper, RIGHT, ModGases.STEAM, 1.5, 200.0);

        // Then cold enough for one craft each, and still no power to cool again afterwards.
        helper.runAfterDelay(20, () -> {
            hot.setMachineTemperature(RECIPE_MAX_TEMPERATURE);
            cold.setMachineTemperature(RECIPE_MAX_TEMPERATURE);
        });

        helper.succeedWhen(() -> {
            if (countOf(hot, Items.ICE) < 1 || countOf(cold, Items.ICE) < 1) {
                helper.fail("Both condensers should have frozen one ice");
            }
            if (cold.getMachineTemperature() <= RECIPE_MAX_TEMPERATURE) {
                helper.fail("A craft cost the cold-fed machine nothing");
            }
            if (hot.getMachineTemperature() <= cold.getMachineTemperature()) {
                helper.fail("Hot Steam warmed the machine to " + hot.getMachineTemperature()
                        + " K, no more than cold Steam's " + cold.getMachineTemperature() + " K");
            }
        });
    }

    /** A product with nowhere to go leaves its gas alone and says so. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void skipsARecipeWhoseOutputIsFull(GameTestHelper helper) {
        CryoCondenserBlockEntity condenser = place(helper, CONDENSER);
        condenser.setMachineTemperature(200.0);
        IItemHandlerModifiable slots = (IItemHandlerModifiable) condenser.getMenuInventory();
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            slots.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        }
        addGas(helper, CONDENSER, ModGases.STEAM, 2.0, 300.0);

        helper.runAfterDelay(20, () -> {
            if (gasAt(helper, CONDENSER, ModGases.STEAM) < 2.0 - 1e-6) {
                helper.fail("Steam was taken with nowhere to put the ice");
            }
            if (condenser.getStatus() != CryoCondenserBlockEntity.Status.OUTPUT_FULL) {
                helper.fail("Expected OUTPUT_FULL, got " + condenser.getStatus());
            }
            helper.succeed();
        });
    }

    /** Gas no recipe takes is not trapped: it flows back out of the port. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gasItCannotFreezeFlowsBackOut(GameTestHelper helper) {
        place(helper, CONDENSER);
        BlockPos duct = CONDENSER.relative(CryoCondenserBlock.PORT);
        helper.setBlock(duct, ModBlocks.GAS_DUCT.get().defaultBlockState());
        addGas(helper, CONDENSER, air(), 2.0, 300.0);

        helper.succeedWhen(() -> {
            if (gasAt(helper, duct, air()) <= 1e-6) {
                helper.fail("No air has left the condenser yet");
            }
        });
    }

    /** Each round starts just after the recipe that crafted last, wrapping around. */
    @GameTest(template = TEMPLATE)
    public static void roundRobinStartsAfterTheLastCraft(GameTestHelper helper) {
        CondensingRecipe a = testRecipe("a");
        CondensingRecipe b = testRecipe("b");
        CondensingRecipe c = testRecipe("c");
        List<CondensingRecipe> recipes = List.of(a, b, c);

        expectOrder(helper, recipes, null, List.of(a, b, c));
        expectOrder(helper, recipes, a.getId(), List.of(b, c, a));
        expectOrder(helper, recipes, b.getId(), List.of(c, a, b));
        expectOrder(helper, recipes, c.getId(), List.of(a, b, c));
        // A recipe that has since gone: the round starts after where it would sort.
        expectOrder(helper, recipes, ZPSMod.resource("test/ab"), List.of(b, c, a));
        helper.succeed();
    }

    private static CondensingRecipe testRecipe(String name) {
        return new CondensingRecipe(ZPSMod.resource("test/" + name),
                new GasOutput(ModGases.STEAM.getResourceLocation(), 1.0), new ItemStack(Items.ICE), 250.0, 100.0);
    }

    private static void expectOrder(GameTestHelper helper, List<CondensingRecipe> recipes,
                                    ResourceLocation lastCrafted, List<CondensingRecipe> expected) {
        List<CondensingRecipe> actual = CryoCondenserBlockEntity.roundRobinOrder(recipes, lastCrafted);
        if (!actual.equals(expected)) {
            helper.fail("After " + lastCrafted + " expected " + ids(expected) + ", got " + ids(actual));
        }
    }

    private static List<ResourceLocation> ids(List<CondensingRecipe> recipes) {
        return recipes.stream().map(CondensingRecipe::getId).toList();
    }

    /** The machine temperature and stored FE survive a save and load. */
    @GameTest(template = TEMPLATE)
    public static void keepsItsStateAcrossAReload(GameTestHelper helper) {
        CryoCondenserBlockEntity condenser = place(helper, CONDENSER);
        fillEnergy(condenser);
        condenser.setMachineTemperature(123.0);
        CompoundTag saved = condenser.saveWithoutMetadata();

        CryoCondenserBlockEntity reloaded = new CryoCondenserBlockEntity(
                helper.absolutePos(CONDENSER), condenser.getBlockState());
        reloaded.load(saved);
        if (Math.abs(reloaded.getMachineTemperature() - 123.0) > 1e-6) {
            helper.fail("Temperature not restored: " + reloaded.getMachineTemperature());
        }
        if (energy(reloaded).getEnergyStored() != energy(condenser).getEnergyStored()) {
            helper.fail("Energy not restored");
        }
        helper.succeed();
    }
}
