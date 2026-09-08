package g_mungus.zps.menu;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.gas.VaporizerBlockEntity;
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
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class VaporizerMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = VaporizerBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    /** Where the three ingredient slots sit on the texture. */
    private static final int MACHINE_SLOT_X = 62;
    private static final int MACHINE_SLOT_Y = 52;

    private static final int DATA_COUNT = 5;

    private final VaporizerBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public VaporizerMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    private VaporizerMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, resolveBlockEntity(inventory.player.level(), pos), new SimpleContainerData(DATA_COUNT));
    }

    public VaporizerMenu(int containerId, Inventory inventory, VaporizerBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.VAPORIZER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        Level level = blockEntity.getLevel();
        this.access = level == null ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(level, blockEntity.getBlockPos());

        for (int slot = 0; slot < MACHINE_SLOT_COUNT; slot++) {
            this.addSlot(new SlotItemHandler(blockEntity.getMenuInventory(), slot,
                    MACHINE_SLOT_X + slot * 18, MACHINE_SLOT_Y));
        }

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

    private static VaporizerBlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof VaporizerBlockEntity vaporizer) {
            return vaporizer;
        }
        throw new IllegalStateException("Missing vaporizer block entity at " + pos);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, ModBlocks.VAPORIZER.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < MACHINE_SLOT_COUNT) {
                if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.slots.get(0).mayPlace(stack)) {
                if (!this.moveItemStackTo(stack, 0, MACHINE_SLOT_COUNT, false)) {
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

    /** The machine's temperature, in Kelvin. */
    public double getTemperature() {
        return data.get(2) / 10.0;
    }

    /** What the heater is working toward, in Kelvin, or zero when it is not. */
    public double getTargetTemperature() {
        return data.get(3) / 10.0;
    }

    public VaporizerBlockEntity.Status getStatus() {
        return VaporizerBlockEntity.Status.byOrdinal(data.get(4));
    }

    public VaporizerBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
