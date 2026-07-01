package g_mungus.zps.blockentity;

import g_mungus.zps.block.RollingMillBlock;
import g_mungus.zps.menu.RollingMillMenu;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.RollingRecipe;
import g_mungus.zps.recipe.RollingRecipeSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RollingMillBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    private static final int MAX_ENERGY = 8192;
    private static final int MAX_RECEIVE = 512;

    private final MillEnergyStorage energyStorage = new MillEnergyStorage();
    private final MillInventory inventory = new MillInventory();
    /** Capability view: automation may only extract from the output slot, never the ingredient slot. */
    private final IItemHandler automationHandler = new OutputOnlyExtractionHandler(inventory);
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                case 2 -> progress;
                case 3 -> maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergyStoredExact(value);
                case 2 -> progress = value;
                case 3 -> maxProgress = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    private int progress;
    private int maxProgress = RollingRecipeSerializer.DEFAULT_PROCESS_TIME;
    @Nullable
    private RollingRecipe cachedRecipe;

    public RollingMillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROLLING_MILL.get(), pos, state);
    }

    /** Capability handler for hoppers/pipes: extraction is restricted to the output slot. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return automationHandler;
    }

    /** Unrestricted handler for the GUI, so players can still reclaim ingredients from the input slot. */
    public IItemHandler getMenuInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    /** True on both sides: driven by the synced WORKING blockstate so the visual can read it. */
    public boolean isWorking() {
        BlockState state = getBlockState();
        return state.hasProperty(RollingMillBlock.WORKING) && state.getValue(RollingMillBlock.WORKING);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        RollingRecipe recipe = getActiveRecipe();
        if (recipe == null || !canOutput(recipe)) {
            resetProgress();
            setWorking(false);
            return;
        }

        maxProgress = recipe.processTime();
        if (energyStorage.getEnergyStored() >= recipe.energyPerTick()) {
            energyStorage.consume(recipe.energyPerTick());
            progress++;
            setWorking(true);
            if (progress >= maxProgress) {
                craft(recipe);
                progress = 0;
            }
            setChanged();
        } else {
            // Out of power: hold progress but stop the animation.
            setWorking(false);
        }
    }

    @Nullable
    private RollingRecipe getActiveRecipe() {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            cachedRecipe = null;
            return null;
        }
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        if (cachedRecipe != null && cachedRecipe.matches(recipeInput, level)) {
            return cachedRecipe;
        }
        cachedRecipe = level.getRecipeManager()
                .getRecipeFor(ModRecipes.ROLLING_TYPE.get(), recipeInput, level)
                .map(holder -> holder.value())
                .orElse(null);
        return cachedRecipe;
    }

    private boolean canOutput(RollingRecipe recipe) {
        ItemStack result = recipe.getResultItem(level.registryAccess());
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return false;
        }
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void craft(RollingRecipe recipe) {
        ItemStack result = recipe.assemble(new SingleRecipeInput(inventory.getStackInSlot(INPUT_SLOT)), level.registryAccess());
        inventory.getStackInSlot(INPUT_SLOT).shrink(1);
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, result.copy());
        } else {
            output.grow(result.getCount());
        }
        cachedRecipe = null;
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    private void setWorking(boolean working) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(RollingMillBlock.WORKING)) {
            return;
        }
        if (state.getValue(RollingMillBlock.WORKING) != working) {
            level.setBlock(worldPosition, state.setValue(RollingMillBlock.WORKING, working), Block.UPDATE_ALL);
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (tag.contains("Energy")) {
            energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        }
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.rolling_mill");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player player) {
        return new RollingMillMenu(containerId, inventory, this, dataAccess);
    }

    private boolean hasRollingRecipe(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return !stack.isEmpty();
        }
        return level.getRecipeManager()
                .getRecipeFor(ModRecipes.ROLLING_TYPE.get(), new SingleRecipeInput(stack), level)
                .isPresent();
    }

    private class MillInventory extends ItemStackHandler {
        private MillInventory() {
            super(2);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == INPUT_SLOT && hasRollingRecipe(stack);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // The output slot never accepts insertion (from hoppers or the GUI).
            if (slot == OUTPUT_SLOT) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == INPUT_SLOT) {
                cachedRecipe = null;
            }
        }
    }

    /** Delegating handler that blocks extraction from every slot except the output. */
    private static class OutputOnlyExtractionHandler implements IItemHandler {
        private final IItemHandler delegate;

        private OutputOnlyExtractionHandler(IItemHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
    }

    private static class MillEnergyStorage extends EnergyStorage {
        private MillEnergyStorage() {
            super(MAX_ENERGY, MAX_RECEIVE, 0);
        }

        private void consume(int amount) {
            energy = Math.max(0, energy - amount);
        }

        private void setEnergyStoredExact(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }
}
