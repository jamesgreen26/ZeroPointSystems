package g_mungus.zps.blockentity;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.StepdownTransformerBlock;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StepUpTransformerBlockEntity extends NetworkTerminal {
    private final EnergyStorage energyHandler;
    private BlockCapabilityCache<IEnergyStorage, @Nullable Direction> inputEnergyCache;
    private BlockCapabilityCache<IEnergyStorage, @Nullable Direction> outputEnergyCache;

    public StepUpTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPUP_TRANSFORMER.get(), pos, state);
        this.energyHandler = new EnergyStorage(5000, 1000, 1000);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            Direction facing = getBlockState().getValue(TransformerBlock.FACING);
            BlockPos inputPos = getBlockPos().relative(facing);
            BlockPos outputPos = getBlockPos().relative(facing.getOpposite());

            // Create caches for input and output energy capabilities
            this.inputEnergyCache = BlockCapabilityCache.create(
                Capabilities.EnergyStorage.BLOCK,
                serverLevel,
                inputPos,
                facing.getOpposite(),
                () -> !this.isRemoved(),
                () -> {} // No-op invalidation listener
            );

            this.outputEnergyCache = BlockCapabilityCache.create(
                Capabilities.EnergyStorage.BLOCK,
                serverLevel,
                outputPos,
                facing,
                () -> !this.isRemoved(),
                () -> {} // No-op invalidation listener
            );
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StepUpTransformerBlockEntity blockEntity) {
        if (level.isClientSide()) return;

        // Try to extract energy from the input side
        IEnergyStorage inputStorage = blockEntity.inputEnergyCache != null ? blockEntity.inputEnergyCache.getCapability() : null;
        if (inputStorage != null && inputStorage.canExtract()) {
            int canStore = Math.min(1000, blockEntity.energyHandler.getMaxEnergyStored() - blockEntity.energyHandler.getEnergyStored());
            int energyToExtract = Math.min(inputStorage.getEnergyStored(), canStore);
            int energyExtracted = inputStorage.extractEnergy(energyToExtract, false);
            if (energyExtracted > 0) {
                blockEntity.energyHandler.receiveEnergy(energyExtracted, false);
            }
        }

        // Distribute energy to connected terminals
        List<NetworkNode> terminals = blockEntity.getTerminals(Channels.MAIN);
        AtomicInteger receivingTerminalCount = new AtomicInteger(0);
        
        // First pass: count valid receiving targets
        terminals.forEach(node -> {
            BlockState state1 = level.getBlockState(node.pos());
            if (state1.is(ModBlocks.STEPDOWN_TRANSFORMER.get())) {
                Direction dir = state1.getValue(StepdownTransformerBlock.FACING);
                BlockPos targetPos = node.pos().relative(dir);
                BlockEntity targetEntity = level.getBlockEntity(targetPos);
                
                if (targetEntity != null) {
                    IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, dir.getOpposite());
                    if (storage != null && storage.canReceive() && storage.getMaxEnergyStored() > storage.getEnergyStored()) {
                        receivingTerminalCount.incrementAndGet();
                    }
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
                    BlockPos targetPos = node.pos().relative(dir);
                    BlockEntity targetEntity = level.getBlockEntity(targetPos);
                    
                    if (targetEntity != null) {
                        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, dir.getOpposite());
                        if (storage != null && storage.canReceive()) {
                            int energySent = blockEntity.energyHandler.extractEnergy(energyPerTransformer, false);
                            if (energySent > 0) {
                                storage.receiveEnergy(energySent, false);
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (level != null) {
            energyHandler.deserializeNBT(level.registryAccess(), tag.get("Energy"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (level != null) {
            tag.put("Energy", energyHandler.serializeNBT(level.registryAccess()));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (inputEnergyCache != null) {
            inputEnergyCache = null;
        }
        if (outputEnergyCache != null) {
            outputEnergyCache = null;
        }
    }
} 