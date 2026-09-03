package g_mungus.zps.menu;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.SieveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Five slots in a row, positioned to match the vanilla hopper GUI texture that {@code SieveScreen} draws.
 */
public class SieveMenu extends AbstractContainerMenu {
    private static final int SIEVE_START = 0;
    private static final int SIEVE_END = SIEVE_START + SieveBlockEntity.SLOT_COUNT;                 // 5
    private static final int PLAYER_INVENTORY_START = SIEVE_END;                                  // 5
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;                 // 32
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;                                // 32
    private static final int HOTBAR_END = HOTBAR_START + 9;                                      // 41

    // Hopper layout: the row of five sits at y 20, the player inventory below it at y 51 / 109.
    private static final int SIEVE_ROW_LEFT = 44;
    private static final int SIEVE_ROW_TOP = 20;
    private static final int PLAYER_INV_LEFT = 8;
    private static final int PLAYER_INV_TOP = 51;
    private static final int HOTBAR_TOP = 109;

    private final SieveBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public SieveMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, resolveBlockEntity(inventory.player.level(), buffer.readBlockPos()));
    }

    public SieveMenu(int containerId, Inventory inventory, SieveBlockEntity blockEntity) {
        super(ModMenus.SIEVE.get(), containerId);
        this.blockEntity = blockEntity;
        Level level = blockEntity.getLevel();
        this.access = level == null ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(level, blockEntity.getBlockPos());

        for (int slot = 0; slot < SieveBlockEntity.SLOT_COUNT; ++slot) {
            this.addSlot(new SlotItemHandler(blockEntity.getInventory(), slot, SIEVE_ROW_LEFT + slot * 18, SIEVE_ROW_TOP));
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INV_LEFT + col * 18, PLAYER_INV_TOP + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inventory, col, PLAYER_INV_LEFT + col * 18, HOTBAR_TOP));
        }
    }

    private static SieveBlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SieveBlockEntity sieve) {
            return sieve;
        }
        throw new IllegalStateException("Missing sieve block entity at " + pos);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.SIEVE.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index >= SIEVE_START && index < SIEVE_END) {
                if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, SIEVE_START, SIEVE_END, false)) {
                // Otherwise shuffle between the inventory rows and the hotbar.
                if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
                    if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    public SieveBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
