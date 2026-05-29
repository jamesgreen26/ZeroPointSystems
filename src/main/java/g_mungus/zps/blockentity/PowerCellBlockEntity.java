package g_mungus.zps.blockentity;

import g_mungus.zps.block.PowerCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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

public class PowerCellBlockEntity extends BlockEntity {
    private static final int MAX_ENERGY = 256_000;
    private static final int MAX_TRANSFER = 16_384;

    private final EnergyStorage energyStorage = new EnergyStorage(MAX_ENERGY, MAX_TRANSFER, MAX_TRANSFER) {
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

    public PowerCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CELL.get(), pos, state);
    }

    public void serverTick() {
        updateFillLevel();
    }

    private void onEnergyChanged() {
        setChanged();
        updateFillLevel();
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

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Energy")) {
            energyStorage.receiveEnergy(tag.getInt("Energy"), false);
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
