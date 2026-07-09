package g_mungus.zps.menu;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssemblerMenu extends RecipeBookMenu<CraftingInput, CraftingRecipe> {
    /** Menu index of the output slot (after the 25 pattern + 12 input slots). */
    private static final int RESULT_SLOT_INDEX = AssemblerBlockEntity.PATTERN_SLOTS + AssemblerBlockEntity.INPUT_SLOTS;

    // Slot layout (must match the GUI texture and AssemblerScreen). Drawn area is 280x166 (vanilla
    // villager-menu dimensions). Far left: 5x5 crafting pattern; top right: 4x3 input buffer, a single
    // enlarged output slot, and the energy meter; bottom right: player inventory.
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 34;
    public static final int INPUT_LEFT = 112;
    public static final int INPUT_TOP = 16;
    // Single output slot, centered inside its 26x26 well (item area is 16x16).
    public static final int OUTPUT_LEFT = 220;
    public static final int OUTPUT_TOP = 35;

    private static final int GHOST_START = 0;
    private static final int GHOST_END = GHOST_START + AssemblerBlockEntity.PATTERN_SLOTS;        // 25
    private static final int INPUT_START = GHOST_END;
    private static final int INPUT_END = INPUT_START + AssemblerBlockEntity.INPUT_SLOTS;           // 43
    private static final int OUTPUT_START = INPUT_END;
    private static final int OUTPUT_END = OUTPUT_START + AssemblerBlockEntity.OUTPUT_SLOTS;         // 52
    private static final int PLAYER_INVENTORY_START = OUTPUT_END;                                   // 52
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;                    // 79
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;                                    // 79
    private static final int HOTBAR_END = HOTBAR_START + 9;                                          // 88

    private static final int PLAYER_INV_LEFT = 112;
    private static final int PLAYER_INV_TOP = 84;
    private static final int HOTBAR_TOP = 142;

    private final AssemblerBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /**
     * Client-only view state: when {@code true} the right-hand grey panels (input/output/energy and the
     * player inventory) are hidden. Making those slots inactive keeps rendering, hover, and click hit-testing
     * consistent. Never synced — purely local GUI toggling.
     */
    private boolean rightPanelCollapsed = false;

    public AssemblerMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    private AssemblerMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, resolveBlockEntity(inventory.player.level(), pos), new SimpleContainerData(4));
    }

    public AssemblerMenu(int containerId, Inventory inventory, AssemblerBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.ASSEMBLER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        Level level = blockEntity.getLevel();
        this.access = level == null ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(level, blockEntity.getBlockPos());

        IItemHandler pattern = blockEntity.getPatternInventory();
        IItemHandler input = blockEntity.getInputInventory();
        IItemHandler output = blockEntity.getOutputInventory();

        // 5x5 ghost/pattern grid.
        for (int row = 0; row < AssemblerBlockEntity.GRID_HEIGHT; ++row) {
            for (int col = 0; col < AssemblerBlockEntity.GRID_WIDTH; ++col) {
                int index = col + row * AssemblerBlockEntity.GRID_WIDTH;
                this.addSlot(new GhostSlot(pattern, index, GRID_LEFT + col * 18, GRID_TOP + row * 18));
            }
        }

        // 4x3 input buffer (4 columns, 3 rows) on the top right.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 4; ++col) {
                int index = col + row * 4;
                this.addSlot(new CollapsibleSlotItemHandler(input, index, INPUT_LEFT + col * 18, INPUT_TOP + row * 18));
            }
        }

        // Single (enlarged) output slot (extraction only from the GUI).
        this.addSlot(new OutputSlot(output, 0, OUTPUT_LEFT, OUTPUT_TOP));

        // Player inventory + hotbar.
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new CollapsibleSlot(inventory, col + row * 9 + 9, PLAYER_INV_LEFT + col * 18, PLAYER_INV_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new CollapsibleSlot(inventory, col, PLAYER_INV_LEFT + col * 18, HOTBAR_TOP));
        }

        this.addDataSlots(data);
    }

    private static AssemblerBlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AssemblerBlockEntity assembler) {
            return assembler;
        }
        throw new IllegalStateException("Missing assembler block entity at " + pos);
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        // Ghost slots stamp a display-only copy of the carried item without consuming it. Routed through
        // the block entity so the parallel ingredient matcher stays in sync (manual = null = exact match).
        if (slotId >= GHOST_START && slotId < GHOST_END) {
            ItemStack carried = getCarried();
            int patternIndex = slotId - GHOST_START;
            if (carried.isEmpty() || button == 1) {
                blockEntity.setPatternCell(patternIndex, null, ItemStack.EMPTY);
            } else {
                blockEntity.setPatternCell(patternIndex, null, carried);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Called server-side when a recipe is clicked in the embedded recipe book. The assembler's grid is a
     * display-only template, so — unlike a vanilla crafting menu — we do NOT move real items into slots
     * (which is what {@code ServerPlaceRecipe} would do). Instead we translate the recipe's ingredients into
     * ghost pattern cells, mirroring the {@code set_recipe} command's behaviour.
     */
    @Override
    public void handlePlacement(boolean placeAll, @NotNull RecipeHolder<?> recipeHolder, @NotNull ServerPlayer player) {
        // Always start from an empty pattern so no cells from a previous recipe (or manual stamping) can survive,
        // even if the recipe below contributes fewer cells than were already set.
        blockEntity.clearPattern();

        Recipe<?> recipe = recipeHolder.value();
        List<Ingredient> ingredients = recipe.getIngredients();
        List<Ingredient> grid = new ArrayList<>(Collections.nCopies(AssemblerBlockEntity.PATTERN_SLOTS, Ingredient.EMPTY));

        // Determine the recipe's bounding box. Shaped recipes carry their own width/height; shapeless recipes
        // are packed row-major into a crafting-grid-sized (up to 3 wide) box.
        int recipeWidth;
        int recipeHeight;
        if (recipe instanceof ShapedRecipe shaped) {
            recipeWidth = shaped.getWidth();
            recipeHeight = shaped.getHeight();
        } else {
            int count = ingredients.size();
            recipeWidth = Math.max(1, Math.min(count, 3));
            recipeHeight = (count + recipeWidth - 1) / recipeWidth;
        }

        // Center the box in the 5x5 pattern, rounding toward the top-left when it can't sit dead-centre.
        int placedWidth = Math.min(recipeWidth, AssemblerBlockEntity.GRID_WIDTH);
        int placedHeight = Math.min(recipeHeight, AssemblerBlockEntity.GRID_HEIGHT);
        int offsetX = (AssemblerBlockEntity.GRID_WIDTH - placedWidth) / 2;
        int offsetY = (AssemblerBlockEntity.GRID_HEIGHT - placedHeight) / 2;
        for (int row = 0; row < placedHeight; row++) {
            for (int col = 0; col < placedWidth; col++) {
                int source = row * recipeWidth + col;
                if (source < ingredients.size()) {
                    int cell = (row + offsetY) * AssemblerBlockEntity.GRID_WIDTH + (col + offsetX);
                    grid.set(cell, ingredients.get(source));
                }
            }
        }

        blockEntity.setPattern(grid);
        broadcastChanges();
    }

    @Override
    public void fillCraftSlotsStackedContents(@NotNull StackedContents itemHelper) {
        // Count the assembler's input buffer (the items it actually crafts from) toward the recipe book's
        // "craftable" detection, alongside the player inventory the book already accounts for.
        IItemHandler input = blockEntity.getInputInventory();
        for (int i = 0; i < input.getSlots(); i++) {
            itemHelper.accountSimpleStack(input.getStackInSlot(i));
        }
    }

    @Override
    public void clearCraftingContent() {
        blockEntity.clearPattern();
    }

    @Override
    public boolean recipeMatches(@NotNull RecipeHolder<CraftingRecipe> recipe) {
        // Templates aren't tracked against vanilla recipes; never treat one as already-placed.
        return false;
    }

    @Override
    public int getResultSlotIndex() {
        return RESULT_SLOT_INDEX;
    }

    @Override
    public int getGridWidth() {
        return AssemblerBlockEntity.GRID_WIDTH;
    }

    @Override
    public int getGridHeight() {
        return AssemblerBlockEntity.GRID_HEIGHT;
    }

    @Override
    public int getSize() {
        return AssemblerBlockEntity.PATTERN_SLOTS;
    }

    @Override
    public @NotNull RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    public boolean shouldMoveToInventory(int slotIndex) {
        return false;
    }

    /** Whether the right-hand grey panels are currently hidden (client view state). */
    public boolean isRightPanelCollapsed() {
        return rightPanelCollapsed;
    }

    /** Toggles right-panel visibility and returns the new state (client view state only). */
    public boolean toggleRightPanel() {
        rightPanelCollapsed = !rightPanelCollapsed;
        return rightPanelCollapsed;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.ASSEMBLER.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        // Ghost slots hold no real items; nothing to shift-move.
        if (index >= GHOST_START && index < GHOST_END) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index >= INPUT_START && index < OUTPUT_END) {
                // From either buffer back into the player inventory.
                if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_INVENTORY_START && index < HOTBAR_END) {
                // From the player inventory into the input buffer.
                if (!this.moveItemStackTo(stack, INPUT_START, INPUT_END, false)) {
                    // Otherwise shuffle between inventory rows and the hotbar.
                    if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
                        if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    public int getEnergyStored() {
        return data.get(0);
    }

    public int getMaxEnergyStored() {
        return data.get(1);
    }

    public int getProgress() {
        return data.get(2);
    }

    public int getMaxProgress() {
        return data.get(3);
    }

    public AssemblerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** Display-only slot: never accepts drag/shift placement or pickup; click handling is done in {@link #clicked}. */
    private static class GhostSlot extends SlotItemHandler {
        private GhostSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return false;
        }
    }

    /** Output buffer slot: crafted results only, never accepts manual insertion. Hidden when collapsed. */
    private class OutputSlot extends SlotItemHandler {
        private OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }

        @Override
        public boolean isActive() {
            return !rightPanelCollapsed;
        }
    }

    /** Input buffer slot that hides (deactivates) when the right panel is collapsed. */
    private class CollapsibleSlotItemHandler extends SlotItemHandler {
        private CollapsibleSlotItemHandler(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !rightPanelCollapsed;
        }
    }

    /** Player inventory/hotbar slot that hides (deactivates) when the right panel is collapsed. */
    private class CollapsibleSlot extends Slot {
        private CollapsibleSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !rightPanelCollapsed;
        }
    }
}
