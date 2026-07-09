package g_mungus.zps.blockentity;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.create.MechanicalCraftingCompat;
import g_mungus.zps.menu.AssemblerMenu;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated crafting machine. A 5x5 ghost "pattern" grid defines a recipe; when the block is powered
 * by redstone, matching ingredients are pulled from the input buffer, the recipe is crafted, and the
 * result is deposited in the output buffer. Each craft consumes a fixed {@link #ENERGY_PER_CRAFT} FE.
 * Supports vanilla shaped/shapeless recipes and (when Create is loaded) Create mechanical crafting.
 */
public class AssemblerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int GRID_WIDTH = 5;
    public static final int GRID_HEIGHT = 5;
    public static final int PATTERN_SLOTS = GRID_WIDTH * GRID_HEIGHT; // 25
    public static final int INPUT_SLOTS = 12; // 4x3
    public static final int OUTPUT_SLOTS = 1; // 1x1

    private static final int MAX_ENERGY = 8192;
    private static final int MAX_RECEIVE = 512;
    private static final int ENERGY_PER_CRAFT = 256;
    /** Ticks between craft attempts while powered. */
    private static final int CRAFT_COOLDOWN = 20;

    private final AssemblerEnergyStorage energyStorage = new AssemblerEnergyStorage();
    /** Display-only recipe template; never holds real inventory. Synced to the client via the container. */
    private final ItemStackHandler pattern = new PatternInventory();
    private final ItemStackHandler input = new BufferInventory(INPUT_SLOTS);
    private final ItemStackHandler output = new BufferInventory(OUTPUT_SLOTS);
    /** Capability view for automation: insert into the input buffer, extract from the output buffer. */
    private final IItemHandler automationHandler = new InputOutputHandler();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                energyStorage.setEnergyStoredExact(value);
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    private int cooldown;
    /** Cached crafting result for the current pattern; null means "not yet resolved" for the pattern. */
    @Nullable
    private ItemStack cachedResult;
    private boolean resultResolved;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLER.get(), pos, state);
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return automationHandler;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    /** GUI-facing handlers (unrestricted, so players can freely manage all slots). */
    public IItemHandler getPatternInventory() {
        return pattern;
    }

    public IItemHandler getInputInventory() {
        return input;
    }

    public IItemHandler getOutputInventory() {
        return output;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!level.hasNeighborSignal(worldPosition)) {
            cooldown = 0;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = CRAFT_COOLDOWN;
        tryCraft();
    }

    private void tryCraft() {
        if (energyStorage.getEnergyStored() < ENERGY_PER_CRAFT) {
            return;
        }
        ItemStack result = getResult();
        if (result == null || result.isEmpty()) {
            return;
        }

        // Reserve one matching input item per non-empty pattern cell.
        int[] reserved = new int[input.getSlots()];
        for (int i = 0; i < pattern.getSlots(); i++) {
            ItemStack needed = pattern.getStackInSlot(i);
            if (needed.isEmpty()) {
                continue;
            }
            if (!reserveOne(needed, reserved)) {
                return; // Missing an ingredient; nothing crafted.
            }
        }

        if (!insertResult(result, true)) {
            return; // No room in the output buffer.
        }

        // Commit: pull the reserved ingredients, deposit the result, consume energy.
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (reserved[slot] > 0) {
                input.extractItem(slot, reserved[slot], false);
            }
        }
        insertResult(result, false);
        energyStorage.consume(ENERGY_PER_CRAFT);
        setChanged();
    }

    /** Marks one more unit of a slot matching {@code needed} as reserved; false if none is available. */
    private boolean reserveOne(ItemStack needed, int[] reserved) {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, needed)
                    && stack.getCount() - reserved[slot] > 0) {
                reserved[slot]++;
                return true;
            }
        }
        return false;
    }

    private boolean insertResult(ItemStack result, boolean simulate) {
        ItemStack remaining = result.copy();
        for (int slot = 0; slot < output.getSlots(); slot++) {
            remaining = output.insertItem(slot, remaining, simulate);
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return remaining.isEmpty();
    }

    /** Resolves (and caches) the crafting result for the current pattern, or null if the pattern makes nothing. */
    @Nullable
    private ItemStack getResult() {
        if (resultResolved) {
            return cachedResult;
        }
        resultResolved = true;
        cachedResult = resolveResult();
        return cachedResult;
    }

    @Nullable
    private ItemStack resolveResult() {
        List<ItemStack> grid = new ArrayList<>(PATTERN_SLOTS);
        boolean anyItem = false;
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            ItemStack stack = pattern.getStackInSlot(i);
            grid.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            anyItem |= !stack.isEmpty();
        }
        if (!anyItem) {
            return null;
        }

        CraftingInput craftingInput = CraftingInput.of(GRID_WIDTH, GRID_HEIGHT, grid);
        ItemStack vanilla = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftingInput, level)
                .map(holder -> holder.value().assemble(craftingInput, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        if (!vanilla.isEmpty()) {
            return vanilla;
        }

        if (Compat.isCreateLoaded()) {
            ItemStack mechanical = MechanicalCraftingCompat.tryAssemble(level, grid, GRID_WIDTH, GRID_HEIGHT);
            if (mechanical != null && !mechanical.isEmpty()) {
                return mechanical;
            }
        }
        return null;
    }

    private void invalidateResult() {
        resultResolved = false;
        cachedResult = null;
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        dropHandler(input);
        dropHandler(output);
        // The ghost pattern holds no real items, so nothing to drop from it.
    }

    private void dropHandler(ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Cooldown", cooldown);
        tag.put("Pattern", pattern.serializeNBT(registries));
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        cooldown = tag.getInt("Cooldown");
        loadFixedSize(pattern, tag, "Pattern", registries);
        loadFixedSize(input, tag, "Input", registries);
        loadFixedSize(output, tag, "Output", registries);
        invalidateResult();
    }

    /**
     * Deserializes a handler while keeping its current (fixed) slot count, ignoring any stale {@code Size}
     * saved by an earlier layout. Stacks in slots beyond the current size are dropped. This prevents an
     * out-of-range crash when the machine's slot counts change between versions.
     */
    private static void loadFixedSize(ItemStackHandler handler, CompoundTag tag, String key,
                                      HolderLookup.Provider registries) {
        if (!tag.contains(key)) {
            return;
        }
        CompoundTag inv = tag.getCompound(key).copy();
        inv.remove("Size");
        handler.deserializeNBT(registries, inv);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.assembler");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory, @NotNull Player player) {
        return new AssemblerMenu(containerId, inventory, this, dataAccess);
    }

    /** Ghost/template slots: at most one item per cell, no automation restrictions needed (menu-driven only). */
    private class PatternInventory extends ItemStackHandler {
        private PatternInventory() {
            super(PATTERN_SLOTS);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            invalidateResult();
            setChanged();
        }
    }

    private class BufferInventory extends ItemStackHandler {
        private BufferInventory(int size) {
            super(size);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }

    /** Automation capability: insertion only into the input buffer, extraction only from the output buffer. */
    private class InputOutputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return input.getSlots() + output.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return isInput(slot) ? input.getStackInSlot(slot) : output.getStackInSlot(slot - input.getSlots());
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!isInput(slot)) {
                return stack;
            }
            return input.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (isInput(slot)) {
                return ItemStack.EMPTY;
            }
            return output.extractItem(slot - input.getSlots(), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return isInput(slot) ? input.getSlotLimit(slot) : output.getSlotLimit(slot - input.getSlots());
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isInput(slot);
        }

        private boolean isInput(int slot) {
            return slot < input.getSlots();
        }
    }

    private static class AssemblerEnergyStorage extends EnergyStorage {
        private AssemblerEnergyStorage() {
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
