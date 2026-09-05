package g_mungus.zps.blockentity;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.StepdownTransformerBlock;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.util.TickAverage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StepUpTransformerBlockEntity extends NetworkTerminalImpl implements EnergyTransferBE {
    private final EnergyStorage energyHandler;
    private long lastHudInfoRequestTick = Long.MIN_VALUE;

    private static final int MAX_TRANSFER = 4096;

    public StepUpTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPUP_TRANSFORMER.get(), pos, state);
        this.energyHandler = new EnergyStorage(MAX_TRANSFER * 2, MAX_TRANSFER, MAX_TRANSFER);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StepUpTransformerBlockEntity blockEntity) {
        if (level.isClientSide()) return;

        // Try to extract energy from the block we're facing
        Direction facing = state.getValue(TransformerBlock.FACING);
        BlockPos targetPos = pos.relative(facing);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);

        int canStore = Math.min(MAX_TRANSFER, blockEntity.energyHandler.getMaxEnergyStored() - blockEntity.energyHandler.getEnergyStored());

        if (targetEntity != null) {
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, targetEntity.getBlockState(), targetEntity, facing.getOpposite());
            if (storage != null && storage.canExtract()) {
                int energyToExtract = Math.min(storage.getEnergyStored(), canStore);
                int energyExtracted = storage.extractEnergy(energyToExtract, false);
                if (energyExtracted > 0) {
                    blockEntity.energyHandler.receiveEnergy(energyExtracted, false);
                }
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
                BlockPos targetPos2 = node.pos().relative(dir);
                BlockEntity targetEntity2 = level.getBlockEntity(targetPos2);
                
                if (targetEntity2 != null) {
                    IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos2, targetEntity2.getBlockState(), targetEntity2, dir.getOpposite());
                    if (storage != null && storage.canReceive() && storage.getMaxEnergyStored() > storage.getEnergyStored()) {
                        receivingTerminalCount.incrementAndGet();
                    }
                }
            } else if (state1.is(ModBlocks.REDSTONE_CONVERTER.get()) && blockEntity.energyHandler.getEnergyStored() > 0) {
                switch (ZPSConfig.getConverterOverpowerBehavior()) {
                    case EXPLODE -> explodeTerminal(level, node);
                    case DESTROY -> destroyTerminal((ServerLevel) level, node);
                }
            }
        });

        // Second pass: distribute energy proportionally
        if (receivingTerminalCount.get() > 0) {
            int availableEnergy = blockEntity.energyHandler.getEnergyStored();
            int energyPerTransformer = Math.min(availableEnergy, MAX_TRANSFER) / receivingTerminalCount.get(); // Send up to MAX_TRANSFER RF/t per transformer
            AtomicInteger transferredThisTick = new AtomicInteger();
            
            terminals.forEach(node -> {
                BlockState state1 = level.getBlockState(node.pos());
                if (state1.is(ModBlocks.STEPDOWN_TRANSFORMER.get())) {
                    Direction dir = state1.getValue(StepdownTransformerBlock.FACING);
                    BlockPos targetPos2 = node.pos().relative(dir);
                    BlockEntity targetEntity2 = level.getBlockEntity(targetPos2);
                    StepDownTransformerBlockEntity targetTransformer =
                            level.getBlockEntity(node.pos()) instanceof StepDownTransformerBlockEntity transformer ? transformer : null;
                    
                    if (targetEntity2 != null) {
                        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos2, targetEntity2.getBlockState(), targetEntity2, dir.getOpposite());
                        if (storage != null && storage.canReceive()) {
                            int canExtract = blockEntity.energyHandler.extractEnergy(energyPerTransformer, true);
                            int canReceive = storage.receiveEnergy(canExtract, true);

                            int toSend = Math.min(canExtract, canReceive);
                            if (toSend > 0) {
                                int extracted = blockEntity.energyHandler.extractEnergy(toSend, false);
                                storage.receiveEnergy(extracted, false);
                                transferredThisTick.addAndGet(extracted);

                                if (targetTransformer != null) {
                                    targetTransformer.updateTransferRate(extracted, level.getGameTime());
                                }
                            }
                        }
                    }
                }
            });

            blockEntity.transferAverage.set(transferredThisTick.get(), level.getGameTime());
        } else {
            blockEntity.transferAverage.set(0, level.getGameTime());
        }
    }

    private static void explodeTerminal(Level level, NetworkNode node) {
        level.destroyBlock(node.pos(), false);
        Vec3 center = node.pos().getCenter();
        level.explode(null, center.x, center.y, center.z, 2f, Level.ExplosionInteraction.BLOCK);
    }

    private static void destroyTerminal(ServerLevel level, NetworkNode node) {
        Vec3 center = node.pos().getCenter();
        level.destroyBlock(node.pos(), true);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y, center.z, 12, 0.2, 0.5, 0.2, 0.02);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            energyHandler.deserializeNBT(registries, tag.get("Energy"));
        } catch (Throwable e) {
            ZPSMod.LOGGER.warn("Failed to deserialize NBT: ", e);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energyHandler.serializeNBT(registries));
    }

    @Override
    public void setLastHudRefreshTick(long ticks) {
        lastHudInfoRequestTick = ticks;
    }

    @Override
    public long getLastHudRefreshTick() {
        return lastHudInfoRequestTick;
    }

    private int hudInfo;
    private final TickAverage transferAverage = new TickAverage(HUD_AVERAGE_WINDOW_TICKS);


    @Override
    public void provideInfo(Integer info) {
        hudInfo = info;
    }

    @Override
    public Integer getInfo() {
        if (level != null && !level.isClientSide()) {
            return transferAverage.average(level.getGameTime());
        }
        return hudInfo;
    }
}
