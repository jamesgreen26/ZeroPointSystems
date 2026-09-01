package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.VaporizerBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.menu.VaporizerMenu;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.VaporizingInput;
import g_mungus.zps.recipe.VaporizingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.Optional;

/**
 * Turns blue ice and lithium into Flux, held in its own pressurised buffer.
 *
 * <p>The vaporizer never pushes gas anywhere: it is a tank node on the network, and Flux leaves
 * only by flowing out along an edge under its own pressure. Production throttles down and stops as
 * the buffer fills, so a vaporizer with nowhere to send Flux idles full rather than voiding it.
 */
public class VaporizerBlockEntity extends GasNodeBlockEntity implements MenuProvider {

    public static final int ICE_SLOT = 0;
    public static final int LITHIUM_SLOT = 1;
    private static final int SLOT_COUNT = 2;

    private static final int MAX_ENERGY = 8192;
    private static final int MAX_RECEIVE = 512;

    /** Shown on the progress arrow while the machine is idle and has no recipe to size it. */
    public static final int DEFAULT_PROCESS_TICKS = 100;

    /** Above this fraction of the buffer's pressure ceiling, production stops. */
    private static final double STALL_AT = 0.9;

    private final VaporizerEnergyStorage energyStorage = new VaporizerEnergyStorage();
    private final ItemStackHandler inventory = new VaporizerInventory();

    private int progress;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                case 2 -> progress;
                case 3 -> currentProcessTime();
                case 4 -> (int) Math.round(bufferFraction() * 1000);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 2) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public VaporizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VAPORIZER.get(), pos, state);
    }

    /** Whether any vaporizing recipe wants this item. Drives slot filtering and shift-clicking. */
    public boolean isIngredient(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return false;
        }
        for (RecipeHolder<VaporizingRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(ModRecipes.VAPORIZING_TYPE.get())) {
            for (Ingredient ingredient : holder.value().ingredientList()) {
                if (ingredient.test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- state ------------------------------------------------------------------------------

    /** How full the buffer is, as a fraction of its pressure ceiling. */
    public double bufferFraction() {
        if (level == null || level.isClientSide()) {
            return Math.min(1.0, getPressure() / VaporizerBlock.MAX_PRESSURE);
        }
        return Math.min(1.0, getPressure() / VaporizerBlock.MAX_PRESSURE);
    }

    public IItemHandler getInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    /** Ticks into the current batch. Zero whenever the machine is not running. */
    public int getProgress() {
        return progress;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    // --- work -------------------------------------------------------------------------------

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        Optional<RecipeHolder<VaporizingRecipe>> match = currentRecipe();

        if (match.isPresent() && canRun(match.get().value())) {
            VaporizingRecipe recipe = match.get().value();
            energyStorage.consume(recipe.energyPerTick());
            progress++;

            if (progress >= recipe.processTime()) {
                progress = 0;
                consumeIngredients(recipe);
                produceGas(recipe);
            }
            setChanged();
        } else if (progress != 0) {
            // Losing power, ingredients or headroom mid-batch resets it rather than banking work.
            progress = 0;
            setChanged();
        }

        syncNodeState();
    }

    private Optional<RecipeHolder<VaporizingRecipe>> currentRecipe() {
        if (level == null) {
            return Optional.empty();
        }
        return level.getRecipeManager()
                .getRecipeFor(ModRecipes.VAPORIZING_TYPE.get(), currentInput(), level);
    }

    private VaporizingInput currentInput() {
        return new VaporizingInput(inventory.getStackInSlot(ICE_SLOT),
                inventory.getStackInSlot(LITHIUM_SLOT));
    }

    /** The active recipe's length, or a default so the idle arrow has a sane scale. */
    private int currentProcessTime() {
        return currentRecipe().map(holder -> holder.value().processTime()).orElse(DEFAULT_PROCESS_TICKS);
    }

    private boolean canRun(VaporizingRecipe recipe) {
        if (energyStorage.getEnergyStored() < recipe.energyPerTick()) {
            return false;
        }
        // A full buffer stalls the machine instead of voiding what it makes.
        return bufferFraction() < STALL_AT;
    }

    private void consumeIngredients(VaporizingRecipe recipe) {
        VaporizingInput input = currentInput();
        int used = -1;
        for (int index = 0; index < recipe.ingredientList().size(); index++) {
            int slot = recipe.slotFor(input, index, used);
            if (slot < 0) {
                continue;
            }
            inventory.extractItem(slot, 1, false);
            used = slot;
        }
    }

    private void produceGas(VaporizingRecipe recipe) {
        GasType gas = recipe.gasType();
        if (gas == null) {
            return;
        }
        KelvinMod.INSTANCE.forceGetKelvin().addGasAtTemperature(
                getDuctNodePosition(), gas, recipe.amount(), recipe.temperature());
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    inventory.getStackInSlot(slot));
        }
    }

    // --- menu -------------------------------------------------------------------------------

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.vaporizer");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                      @NotNull Player player) {
        return new VaporizerMenu(containerId, inventory, this, dataAccess);
    }

    // --- persistence ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
    }

    // --- helpers ----------------------------------------------------------------------------

    private class VaporizerInventory extends ItemStackHandler {
        private VaporizerInventory() {
            super(SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // Recipes match either order, so either ingredient goes in either slot.
            return isIngredient(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }

    private static class VaporizerEnergyStorage extends EnergyStorage {
        private VaporizerEnergyStorage() {
            super(MAX_ENERGY, MAX_RECEIVE, 0);
        }

        private void consume(int amount) {
            this.energy = Math.max(0, this.energy - amount);
        }

        private void setEnergyStoredExact(int value) {
            this.energy = Math.clamp(value, 0, getMaxEnergyStored());
        }
    }
}
