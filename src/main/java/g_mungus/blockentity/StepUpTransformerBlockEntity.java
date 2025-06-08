package g_mungus.blockentity;

import g_mungus.ZPSMod;
import g_mungus.block.cableNetwork.StepdownTransformerBlock;
import g_mungus.block.cableNetwork.TransformerBlock;
import g_mungus.block.cableNetwork.core.Channels;
import g_mungus.block.cableNetwork.core.NetworkNode;
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

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StepUpTransformerBlockEntity extends NetworkTerminal {
    private final EnergyStorage energyHandler;
    private final LazyOptional<IEnergyStorage> energy;

    public StepUpTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPUP_TRANSFORMER.get(), pos, state);
        this.energyHandler = new EnergyStorage(100000, 1000, 1000);
        this.energy = LazyOptional.of(() -> energyHandler);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StepUpTransformerBlockEntity blockEntity) {
        if (level.isClientSide()) return;

        ZPSMod.LOGGER.info("StepUp tick - Energy stored: {}", blockEntity.energyHandler.getEnergyStored());

        // Try to extract energy from the block we're facing
        Direction facing = state.getValue(TransformerBlock.FACING);
        BlockPos targetPos = pos.relative(facing);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        if (targetEntity != null) {
            ZPSMod.LOGGER.info("Found target entity at {}", targetPos);
            targetEntity.getCapability(ForgeCapabilities.ENERGY, facing.getOpposite()).ifPresent(storage -> {
                if (storage.canExtract()) {
                    int energyToExtract = Math.min(storage.getEnergyStored(), 1000);
                    int energyExtracted = storage.extractEnergy(energyToExtract, false);
                    if (energyExtracted > 0) {
                        blockEntity.energyHandler.receiveEnergy(energyExtracted, false);
                        ZPSMod.LOGGER.info("Extracted {} energy from source", energyExtracted);
                    } else {
                        ZPSMod.LOGGER.info("Failed to extract energy from source");
                    }
                } else {
                    ZPSMod.LOGGER.info("Source cannot extract energy");
                }
            });
        } else {
            ZPSMod.LOGGER.info("No target entity found at {}", targetPos);
        }

        // Distribute energy to connected terminals
        List<NetworkNode> terminals = blockEntity.getTerminals(Channels.MAIN);

        ZPSMod.LOGGER.info("terminals: {}", terminals);
        AtomicInteger totalEnergyNeeded = new AtomicInteger(0);
        
        // First pass: calculate total energy needed
        terminals.forEach(node -> {
            BlockEntity targetEntity2 = level.getBlockEntity(node.pos());
            if (targetEntity2 instanceof StepDownTransformerBlockEntity) {
                targetEntity2.getCapability(ForgeCapabilities.ENERGY, level.getBlockState(node.pos()).getValue(StepdownTransformerBlock.FACING)).ifPresent(storage -> {
                    if (storage.canReceive()) {
                        int energyNeeded = storage.getMaxEnergyStored() - storage.getEnergyStored();
                        totalEnergyNeeded.addAndGet(energyNeeded);
                        ZPSMod.LOGGER.info("StepDown at {} needs {} energy", node.pos(), energyNeeded);
                    } else {
                        ZPSMod.LOGGER.info("StepDown at {} cannot receive energy 2", node.pos());
                    }
                });
            } else {
                ZPSMod.LOGGER.info("No StepDown transformer found at {} 2", node.pos());
            }
        });

        // Second pass: distribute energy proportionally
        if (totalEnergyNeeded.get() > 0) {
            int availableEnergy = blockEntity.energyHandler.getEnergyStored();
            ZPSMod.LOGGER.info("Total energy needed: {}, Available: {}", totalEnergyNeeded.get(), availableEnergy);
            
            // Calculate how much energy to send per transformer
            int energyPerTransformer = Math.min(availableEnergy, 1000); // Send up to 1000 RF/t per transformer
            
            terminals.forEach(node -> {
                BlockEntity targetEntity2 = level.getBlockEntity(node.pos());
                if (targetEntity2 instanceof StepDownTransformerBlockEntity) {
                    targetEntity2.getCapability(ForgeCapabilities.ENERGY, level.getBlockState(node.pos()).getValue(StepdownTransformerBlock.FACING)).ifPresent(storage -> {
                        if (storage.canReceive()) {
                            int energySent = blockEntity.energyHandler.extractEnergy(energyPerTransformer, false);
                            if (energySent > 0) {
                                storage.receiveEnergy(energySent, false);
                                ZPSMod.LOGGER.info("Sent {} energy to StepDown at {}", energySent, node.pos());
                            } else {
                                ZPSMod.LOGGER.info("Failed to send energy to StepDown at {}", node.pos());
                            }
                        } else {
                            ZPSMod.LOGGER.info("StepDown at {} cannot receive energy 2", node.pos());
                        }
                    });
                } else {
                    ZPSMod.LOGGER.info("No StepDown transformer found at {} 2", node.pos());
                }
            });
        } else {
            ZPSMod.LOGGER.info("No energy needed by any StepDown transformers");
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
            // Only allow energy extraction on the opposite side of where we're facing
            if (side != null && side == getBlockState().getValue(TransformerBlock.FACING)) {
                return energy.cast();
            } else {
                ZPSMod.LOGGER.info("Energy capability requested from invalid side: {}", side);
            }
        }
        return super.getCapability(cap, side);
    }
} 