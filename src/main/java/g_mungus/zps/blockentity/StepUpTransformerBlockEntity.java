package g_mungus.zps.blockentity;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.StepdownTransformerBlock;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StepUpTransformerBlockEntity extends NetworkTerminal {
    private final EnergyStorage energyHandler;
    private final LazyOptional<IEnergyStorage> energy;

    public StepUpTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPUP_TRANSFORMER.get(), pos, state);
        this.energyHandler = new EnergyStorage(5000, 1000, 1000);
        this.energy = LazyOptional.of(() -> energyHandler);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StepUpTransformerBlockEntity blockEntity) {
        if (level.isClientSide()) return;

        // Try to extract energy from the block we're facing
        Direction facing = state.getValue(TransformerBlock.FACING);
        BlockPos targetPos = pos.relative(facing);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        int canStore = Math.min(1000, blockEntity.energyHandler.getMaxEnergyStored() - blockEntity.energyHandler.getEnergyStored());

        if (targetEntity != null) {
            targetEntity.getCapability(ForgeCapabilities.ENERGY, facing.getOpposite()).ifPresent(storage -> {
                if (storage.canExtract()) {
                    int energyToExtract = Math.min(storage.getEnergyStored(), canStore);
                    int energyExtracted = storage.extractEnergy(energyToExtract, false);
                    if (energyExtracted > 0) {
                        blockEntity.energyHandler.receiveEnergy(energyExtracted, false);
                    }
                }
            });
        }

        // Distribute energy to connected terminals
        List<NetworkNode> terminals = blockEntity.getTerminals(Channels.MAIN);
        AtomicInteger receivingTerminalCount = new AtomicInteger(0);
        
        // First pass: count valid receiving targets
        terminals.forEach(node -> {
            BlockState state1 = level.getBlockState(node.pos());
            if (state1.is(ModBlocks.STEPDOWN_TRANSFORMER.get())) {
                Direction dir = state1.getValue(StepdownTransformerBlock.FACING);
                BlockPos targetPos2 = node.pos().relative(dir);
                BlockEntity targetEntity2 = level.getBlockEntity(targetPos2);
                
                if (targetEntity2 != null) {
                    targetEntity2.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).ifPresent(storage -> {
                        if (storage.canReceive() && storage.getMaxEnergyStored() > storage.getEnergyStored()) {
                            receivingTerminalCount.incrementAndGet();
                        }
                    });
                }
            } else if (state1.is(ModBlocks.REDSTONE_CONVERTER.get()) && blockEntity.energyHandler.getEnergyStored() > 0) {
                level.destroyBlock(node.pos(), false);
                Vec3 center = node.pos().getCenter();
                level.explode(null, center.x, center.y, center.z, 2f, Level.ExplosionInteraction.BLOCK);
            }
        });

        // Second pass: distribute energy proportionally
        if (receivingTerminalCount.get() > 0) {
            int availableEnergy = blockEntity.energyHandler.getEnergyStored();
            int energyPerTransformer = Math.min(availableEnergy, 1000) / receivingTerminalCount.get(); // Send up to 1000 RF/t per transformer
            
            terminals.forEach(node -> {
                BlockState state1 = level.getBlockState(node.pos());
                if (state1.is(ModBlocks.STEPDOWN_TRANSFORMER.get())) {
                    Direction dir = state1.getValue(StepdownTransformerBlock.FACING);
                    BlockPos targetPos2 = node.pos().relative(dir);
                    BlockEntity targetEntity2 = level.getBlockEntity(targetPos2);
                    
                    if (targetEntity2 != null) {
                        targetEntity2.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).ifPresent(storage -> {
                            if (storage.canReceive()) {
                                int energySent = blockEntity.energyHandler.extractEnergy(energyPerTransformer, false);
                                if (energySent > 0) {
                                    storage.receiveEnergy(energySent, false);
                                }
                            }
                        });
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