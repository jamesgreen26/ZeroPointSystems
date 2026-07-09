package g_mungus.zps.menu;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class AssemblerMenu extends AbstractContainerMenu {
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

    /** Menu-button id for the clear-pattern (trash) button, routed server-side via {@link #clickMenuButton}. */
    public static final int BUTTON_CLEAR_PATTERN = 0;

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

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        if (id == BUTTON_CLEAR_PATTERN) {
            blockEntity.clearPattern();
            return true;
        }
        return super.clickMenuButton(player, id);
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
