package g_mungus.zps.blockentity;

import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.create.MechanicalCraftingCompat;
import g_mungus.zps.item.ModComponents;
import g_mungus.zps.menu.AssemblerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
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
import java.util.Arrays;
import java.util.List;

/**
 * Automated crafting machine. A 5x5 ghost "pattern" grid defines a recipe; when the block is powered
 * by redstone, matching ingredients are pulled from the input buffer, the recipe is crafted, and the
 * result is deposited in the output buffer. Working draws {@link #ENERGY_PER_TICK} FE per tick.
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
    /** FE drawn each tick while the machine is actively working, like the rolling mill. */
    private static final int ENERGY_PER_TICK = 16;
    /** Ticks the progress arrow takes to fill before a craft completes (front-loaded delay). */
    private static final int PROCESS_TIME = 20;

    private final AssemblerEnergyStorage energyStorage = new AssemblerEnergyStorage();
    /** Display-only recipe template; never holds real inventory. Synced to the client via the container. */
    private final ItemStackHandler pattern = new PatternInventory();
    /**
     * Authoritative per-cell matcher, parallel to the display {@link #pattern}. A non-null entry (set by the
     * {@code set_recipe} command from a recipe's {@link Ingredient}) matches input via {@link Ingredient#test}
     * — preserving item tags. A {@code null} entry means "match the display item exactly" (manual editing).
     * Server-side only; never synced.
     */
    private final Ingredient[] patternIngredients = new Ingredient[PATTERN_SLOTS];
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
                case 2 -> progress;
                case 3 -> PROCESS_TIME;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergyStoredExact(value);
                case 2 -> progress = value;
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

    /**
     * Sets a single pattern cell. A {@code null} ingredient means "match the display item exactly" (manual
     * editing, preserving components); a non-null ingredient enables tag-aware matching via
     * {@link Ingredient#test}. Called by the menu (manual stamping) and the {@code set_recipe} command.
     */
    public void setPatternCell(int index, @Nullable Ingredient ingredient, ItemStack display) {
        if (index < 0 || index >= PATTERN_SLOTS) {
            return;
        }
        patternIngredients[index] = (ingredient != null && !ingredient.isEmpty()) ? ingredient : null;
        ItemStack cell = display.isEmpty() ? ItemStack.EMPTY : display.copyWithCount(1);
        // Tag ingredients get a marker component (the tag ids) so the client can cycle the preview and show
        // a tag tooltip. It rides the normal menu item sync; concrete items get nothing.
        if (!cell.isEmpty()) {
            List<ResourceLocation> tags = tagIds(ingredient);
            if (!tags.isEmpty()) {
                cell.set(ModComponents.GHOST_INGREDIENT_TAGS.get(), tags);
            }
        }
        pattern.setStackInSlot(index, cell);
    }

    /** The item-tag ids an ingredient accepts (empty if it is a concrete-item ingredient). */
    private static List<ResourceLocation> tagIds(@Nullable Ingredient ingredient) {
        if (ingredient == null) {
            return List.of();
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (Ingredient.Value value : ingredient.getValues()) {
            if (value instanceof Ingredient.TagValue tagValue) {
                ids.add(tagValue.tag().location());
            }
        }
        return ids;
    }

    /** Clears the whole pattern (display items and ingredient matchers). */
    public void clearPattern() {
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            setPatternCell(i, null, ItemStack.EMPTY);
        }
    }

    /**
     * Replaces the entire pattern from a 25-cell (row-major 5x5) list of ingredients, clearing whatever was
     * there. Empty ingredients clear the cell; non-empty ones are stored for tag-aware matching and display
     * their first representative item. Used by the {@code set_recipe} command.
     */
    public void setPattern(List<Ingredient> grid) {
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            Ingredient ingredient = i < grid.size() ? grid.get(i) : null;
            if (ingredient == null || ingredient.isEmpty() || ingredient.getItems().length == 0) {
                setPatternCell(i, null, ItemStack.EMPTY);
            } else {
                setPatternCell(i, ingredient, ingredient.getItems()[0]);
            }
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!level.hasNeighborSignal(worldPosition)) {
            resetProgress();
            return;
        }

        ItemStack result = getResult();
        int[] reserved = (result == null || result.isEmpty()) ? null : reserveIngredients();
        boolean craftable = reserved != null && insertResult(result, true);
        if (!craftable) {
            resetProgress();
            return;
        }
        if (energyStorage.getEnergyStored() < ENERGY_PER_TICK) {
            return; // Out of power: hold progress, wait for energy.
        }

        energyStorage.consume(ENERGY_PER_TICK);
        progress++;
        if (progress >= PROCESS_TIME) {
            // Commit: pull the reserved ingredients, deposit the result.
            for (int slot = 0; slot < input.getSlots(); slot++) {
                if (reserved[slot] > 0) {
                    input.extractItem(slot, reserved[slot], false);
                }
            }
            insertResult(result, false);
            progress = 0;
        }
        setChanged();
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    /**
     * Reserves one matching input item per non-empty pattern cell, returning the per-slot reservation
     * counts, or {@code null} if any required ingredient is missing from the input buffer.
     */
    @Nullable
    private int[] reserveIngredients() {
        int[] reserved = new int[input.getSlots()];
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            if (pattern.getStackInSlot(i).isEmpty()) {
                continue;
            }
            if (!reserveOne(i, reserved)) {
                return null;
            }
        }
        return reserved;
    }

    /**
     * Reserves one input item matching pattern cell {@code index}; false if none is available. A tag-aware
     * ingredient (from {@code set_recipe}) matches via {@link Ingredient#test}; otherwise the display item
     * is matched exactly (component-sensitive).
     */
    private boolean reserveOne(int index, int[] reserved) {
        Ingredient ingredient = patternIngredients[index];
        ItemStack display = pattern.getStackInSlot(index);
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() - reserved[slot] <= 0) {
                continue;
            }
            boolean matches = ingredient != null
                    ? ingredient.test(stack)
                    : ItemStack.isSameItemSameComponents(stack, display);
            if (matches) {
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
        tag.putInt("Progress", progress);
        tag.put("Pattern", pattern.serializeNBT(registries));
        tag.put("PatternIngredients", savePatternIngredients(registries));
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
    }

    /** Serializes only the non-null (tag-aware) pattern ingredients, keyed by slot. */
    private ListTag savePatternIngredients(HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            Ingredient ingredient = patternIngredients[i];
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Ingredient", Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow());
            list.add(entry);
        }
        return list;
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        loadFixedSize(pattern, tag, "Pattern", registries);
        loadPatternIngredients(tag, registries);
        loadFixedSize(input, tag, "Input", registries);
        loadFixedSize(output, tag, "Output", registries);
        invalidateResult();
    }

    /** Restores the tag-aware ingredient matchers; absent entries default to exact display-item matching. */
    private void loadPatternIngredients(CompoundTag tag, HolderLookup.Provider registries) {
        Arrays.fill(patternIngredients, null);
        if (!tag.contains("PatternIngredients")) {
            return;
        }
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = tag.getList("PatternIngredients", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= PATTERN_SLOTS) {
                continue;
            }
            Ingredient.CODEC.parse(ops, entry.get("Ingredient")).result()
                    .ifPresent(ingredient -> patternIngredients[slot] = ingredient);
        }
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
