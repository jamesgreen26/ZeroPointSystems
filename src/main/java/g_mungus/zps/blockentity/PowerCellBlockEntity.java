package g_mungus.zps.blockentity;

import g_mungus.zps.block.PowerCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PowerCellBlockEntity extends BlockEntity implements EnergyStorageBE {
    private static final int MAX_ENERGY = 2_097_152;
    private static final int MAX_TRANSFER = 16_384;

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

    private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> energyStorage);
    private int lastSyncedLevel = -1;
    private int lastSentClientEnergy = Integer.MIN_VALUE;
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
        updateFillLevel();
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
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
        syncToClient();
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
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Energy")) {
            energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Energy", energyStorage.getEnergyStored());
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("Energy")) {
            energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        }
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energy.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energy.invalidate();
    }
}
