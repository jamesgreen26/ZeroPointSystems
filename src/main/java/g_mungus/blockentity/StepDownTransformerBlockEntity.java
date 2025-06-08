package g_mungus.blockentity;

import g_mungus.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StepDownTransformerBlockEntity extends NetworkTerminal {
    private final EnergyStorage energyHandler;
    private final LazyOptional<IEnergyStorage> energy;

    public StepDownTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPDOWN_TRANSFORMER.get(), pos, state);
        this.energyHandler = new EnergyStorage(5000, 1000, 1000);
        this.energy = LazyOptional.of(() -> energyHandler);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StepDownTransformerBlockEntity blockEntity) {
        if (level.isClientSide()) return;

        Direction facing = state.getValue(TransformerBlock.FACING);
        BlockPos targetPos = pos.relative(facing);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity != null) {
            targetEntity.getCapability(ForgeCapabilities.ENERGY, facing.getOpposite()).ifPresent(storage -> {
                if (storage.canReceive()) {
                    int energyToSend = Math.min(blockEntity.energyHandler.getEnergyStored(), 1000);
                    if (energyToSend > 0) {
                        int energySent = blockEntity.energyHandler.extractEnergy(energyToSend, false);
                        if (energySent > 0) {
                            storage.receiveEnergy(energySent, false);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyHandler.deserializeNBT(tag.get("Energy"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Energy", energyHandler.serializeNBT());
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            if (side != null && side == getBlockState().getValue(TransformerBlock.FACING)) {
                return energy.cast();
            }
        }
        return super.getCapability(cap, side);
    }
} 