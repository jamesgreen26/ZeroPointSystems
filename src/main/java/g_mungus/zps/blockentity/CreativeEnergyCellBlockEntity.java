package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreativeEnergyCellBlockEntity extends BlockEntity {
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

    public CreativeEnergyCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_ENERGY_CELL.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
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
