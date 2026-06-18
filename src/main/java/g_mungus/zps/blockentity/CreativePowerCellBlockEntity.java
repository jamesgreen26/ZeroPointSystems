package g_mungus.zps.blockentity;

import g_mungus.zps.menu.PowerCellMenu;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
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

    private final ChargeItemStackHandler chargeInventory = new ChargeItemStackHandler();
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
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(side), target.getBlockState(), target, side.getOpposite());
            if (storage != null) {
                storage.receiveEnergy(Integer.MAX_VALUE, false);
            }
        }
    }

    public IItemHandler getChargeInventory() {
        return chargeInventory;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return CREATIVE_ENERGY;
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
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

        IEnergyStorage itemEnergy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (itemEnergy == null || !itemEnergy.canReceive()) {
            return;
        }
        int received = itemEnergy.receiveEnergy(ITEM_CHARGE_TRANSFER, false);
        if (received > 0) {
            chargeInventory.setStackInSlot(0, stack);
            setChanged();
        }
    }

    @Override
    public int getChargeItemEnergyStored() {
        ItemStack stack = chargeInventory.getStackInSlot(0);
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy == null ? 0 : energy.getEnergyStored();
    }

    @Override
    public int getChargeItemMaxEnergyStored() {
        ItemStack stack = chargeInventory.getStackInSlot(0);
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy == null ? 0 : energy.getMaxEnergyStored();
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
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("ChargeItem", chargeInventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ChargeItem")) {
            chargeInventory.deserializeNBT(registries, tag.getCompound("ChargeItem"));
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
