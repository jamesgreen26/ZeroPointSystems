package g_mungus.zps.blockentity;

import g_mungus.zps.block.PowerCellBlock;
import g_mungus.zps.menu.PowerCellMenu;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PowerCellBlockEntity extends BlockEntity implements EnergyStorageBE, MenuProvider, ItemChargingPowerCell {
    private static final int MAX_ENERGY = 2_097_152;
    private static final int MAX_TRANSFER = 16_384;
    private static final int ITEM_CHARGE_TRANSFER = 1024;

    private final SyncedEnergyStorage energyStorage = new SyncedEnergyStorage(MAX_ENERGY, MAX_TRANSFER, MAX_TRANSFER) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                onEnergyChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) {
                onEnergyChanged();
            }
            return extracted;
        }
    };

    private final ChargeItemStackHandler chargeInventory = new ChargeItemStackHandler();
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getMenuEnergyStored();
                case 1 -> getMenuMaxEnergyStored();
                case 2 -> getChargeItemEnergyStored();
                case 3 -> getChargeItemMaxEnergyStored();
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
            return 4;
        }
    };
    private int lastSyncedLevel = -1;
    private int lastSentClientEnergy = Integer.MIN_VALUE;
    private int lastComparatorOutput = -1;
    private float clientSmoothedFill = 0.0f;
    private long lastHudInfoRequestTick = Long.MIN_VALUE;
    private int hudInfo;

    public PowerCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CELL.get(), pos, state);
    }

    private static class SyncedEnergyStorage extends EnergyStorage {
        public SyncedEnergyStorage(int capacity, int maxReceive, int maxExtract) {
            super(capacity, maxReceive, maxExtract);
        }

        public void setEnergyStoredExact(int energy) {
            this.energy = Math.max(0, Math.min(this.capacity, energy));
        }
    }

    public void serverTick() {
        chargeItem();
        updateFillLevel();
        updateComparatorOutput();
    }

    public static boolean isChargeable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy != null && energy.canReceive();
    }

    public IItemHandler getChargeInventory() {
        return chargeInventory;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return chargeInventory;
    }

    @Override
    public int getMenuEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    @Override
    public int getMenuMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
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

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    public int getComparatorOutputSignal() {
        long energyStored = energyStorage.getEnergyStored();
        long maxEnergy = energyStorage.getMaxEnergyStored();
        if (energyStored <= 0 || maxEnergy <= 0) {
            return 0;
        }
        return (int) Math.min(15, Math.ceil((energyStored * 15.0D) / maxEnergy));
    }

    private void chargeItem() {
        if (energyStorage.getEnergyStored() <= 0) {
            return;
        }

        ItemStack stack = chargeInventory.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }

        IEnergyStorage itemEnergy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (itemEnergy == null || !itemEnergy.canReceive()) {
            return;
        }

        int available = energyStorage.extractEnergy(ITEM_CHARGE_TRANSFER, true);
        int accepted = itemEnergy.receiveEnergy(available, true);
        int transfer = Math.min(available, accepted);
        if (transfer <= 0) {
            return;
        }

        int extracted = energyStorage.extractEnergy(transfer, false);
        int received = itemEnergy.receiveEnergy(extracted, false);
        if (received < extracted) {
            energyStorage.receiveEnergy(extracted - received, false);
        }

        chargeInventory.setStackInSlot(0, stack);
        setChanged();
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

    public float getClientSmoothedFill() {
        return clientSmoothedFill;
    }

    public void setClientSmoothedFill(float clientSmoothedFill) {
        this.clientSmoothedFill = clientSmoothedFill;
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
            return energyStorage.getEnergyStored();
        }
        return hudInfo;
    }

    private void onEnergyChanged() {
        setChanged();
        updateFillLevel();
        updateComparatorOutput();
        syncToClient();
    }

    private void updateComparatorOutput() {
        if (level == null || level.isClientSide()) {
            return;
        }

        int comparatorOutput = getComparatorOutputSignal();
        if (comparatorOutput == lastComparatorOutput) {
            return;
        }

        lastComparatorOutput = comparatorOutput;
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    private void updateFillLevel() {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(PowerCellBlock.LEVEL)) {
            return;
        }

        int fillLevel = getFillLevel();
        if (fillLevel == lastSyncedLevel && state.getValue(PowerCellBlock.LEVEL) == fillLevel) {
            return;
        }

        lastSyncedLevel = fillLevel;
        if (state.getValue(PowerCellBlock.LEVEL) != fillLevel) {
            level.setBlock(worldPosition, state.setValue(PowerCellBlock.LEVEL, fillLevel), Block.UPDATE_ALL);
        }
    }

    private int getFillLevel() {
        long energyStored = energyStorage.getEnergyStored();
        long maxEnergy = energyStorage.getMaxEnergyStored();
        if (maxEnergy <= 0) {
            return 0;
        }
        return (int) Math.min(9, (energyStored * 9L) / maxEnergy);
    }

    private void syncToClient() {
        if (level == null || level.isClientSide()) {
            return;
        }
        int energyStored = energyStorage.getEnergyStored();
        if (energyStored == lastSentClientEnergy) {
            return;
        }
        lastSentClientEnergy = energyStored;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.put("ChargeItem", chargeInventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) {
            energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        }
        if (tag.contains("ChargeItem")) {
            chargeInventory.deserializeNBT(registries, tag.getCompound("ChargeItem"));
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
        return Component.translatable("block.zps.power_cell");
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
            return isChargeable(stack);
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
