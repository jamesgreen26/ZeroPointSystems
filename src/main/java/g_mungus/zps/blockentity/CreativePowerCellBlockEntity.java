package g_mungus.zps.blockentity;

import g_mungus.zps.menu.PowerCellMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreativePowerCellBlockEntity extends BlockEntity implements EnergyStorageBE, MenuProvider, ItemChargingPowerCell {
    private static final int ITEM_CHARGE_TRANSFER = 4_096;

    private static final IEnergyStorage CREATIVE_ENERGY = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return maxExtract;
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> CREATIVE_ENERGY);
    private final ChargeItemStackHandler chargeInventory = new ChargeItemStackHandler();
    private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> chargeInventory);
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0, 1 -> INFINITE_ENERGY;
                case 2 -> getChargeItemEnergyStored();
                case 3 -> getChargeItemMaxEnergyStored();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };
    private long lastHudInfoRequestTick = Long.MIN_VALUE;
    private int hudInfo = INFINITE_FE_INFO;

    public CreativePowerCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_POWER_CELL.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        BlockEntity self = level.getBlockEntity(pos);
        if (self instanceof CreativePowerCellBlockEntity creativePowerCell) {
            creativePowerCell.chargeItem();
        }

        for (Direction side : Direction.values()) {
            BlockEntity target = level.getBlockEntity(pos.relative(side));
            if (target == null) {
                continue;
            }
            target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite())
                    .ifPresent(storage -> storage.receiveEnergy(Integer.MAX_VALUE, false));
        }
    }

    public IItemHandler getChargeInventory() {
        return chargeInventory;
    }

    @Override
    public int getMenuEnergyStored() {
        return INFINITE_ENERGY;
    }

    @Override
    public int getMenuMaxEnergyStored() {
        return INFINITE_ENERGY;
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }

        ItemStack stack = chargeInventory.getStackInSlot(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
    }

    private void chargeItem() {
        ItemStack stack = chargeInventory.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }

        stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(itemEnergy -> {
            if (!itemEnergy.canReceive()) {
                return;
            }
            int received = itemEnergy.receiveEnergy(ITEM_CHARGE_TRANSFER, false);
            if (received > 0) {
                chargeInventory.setStackInSlot(0, stack);
                setChanged();
            }
        });
    }

    @Override
    public int getChargeItemEnergyStored() {
        ItemStack stack = chargeInventory.getStackInSlot(0);
        return stack.getCapability(ForgeCapabilities.ENERGY)
                .map(IEnergyStorage::getEnergyStored)
                .orElse(0);
    }

    @Override
    public int getChargeItemMaxEnergyStored() {
        ItemStack stack = chargeInventory.getStackInSlot(0);
        return stack.getCapability(ForgeCapabilities.ENERGY)
                .map(IEnergyStorage::getMaxEnergyStored)
                .orElse(0);
    }

    @Override
    public void setLastHudRefreshTick(long ticks) {
        lastHudInfoRequestTick = ticks;
    }

    @Override
    public long getLastHudRefreshTick() {
        return lastHudInfoRequestTick;
    }

    @Override
    public void provideInfo(Integer info) {
        hudInfo = info;
    }

    @Override
    public Integer getInfo() {
        if (level != null && !level.isClientSide) {
            return INFINITE_FE_INFO;
        }
        return hudInfo;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energy.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return items.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energy.invalidate();
        items.invalidate();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("ChargeItem", chargeInventory.serializeNBT());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("ChargeItem")) {
            chargeInventory.deserializeNBT(tag.getCompound("ChargeItem"));
        }
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.creative_power_cell");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                     @NotNull Player player) {
        return new PowerCellMenu(containerId, inventory, this, dataAccess);
    }

    private class ChargeItemStackHandler extends ItemStackHandler {
        private ChargeItemStackHandler() {
            super(1);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return PowerCellBlockEntity.isChargeable(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }
}
