package g_mungus.zps.menu;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class PowerCellMenu extends AbstractContainerMenu {
    private static final int CHARGE_SLOT = 0;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final PowerCellBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public PowerCellMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    private PowerCellMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, resolveBlockEntity(inventory.player.level(), pos), new SimpleContainerData(4));
    }

    public PowerCellMenu(int containerId, Inventory inventory, PowerCellBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.POWER_CELL.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        Level level = blockEntity.getLevel();
        this.access = level == null ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(level, blockEntity.getBlockPos());

        this.addSlot(new SlotItemHandler(blockEntity.getChargeInventory(), 0, 79, 34));

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    private static PowerCellBlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PowerCellBlockEntity powerCell) {
            return powerCell;
        }
        throw new IllegalStateException("Missing power cell block entity at " + pos);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.POWER_CELL.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == CHARGE_SLOT) {
                if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (PowerCellBlockEntity.isChargeable(stack)) {
                if (!this.moveItemStackTo(stack, CHARGE_SLOT, CHARGE_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
                if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= HOTBAR_START && index < HOTBAR_END
                    && !this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
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

    public int getItemEnergyStored() {
        return data.get(2);
    }

    public int getItemMaxEnergyStored() {
        return data.get(3);
    }

    public PowerCellBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
