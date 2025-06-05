package g_mungus.blockentity;

import g_mungus.block.ModBlocks;
import g_mungus.block.cableNetwork.RedstoneConverterBlock;
import g_mungus.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RedstoneConverterBlockEntity extends TransformerBlockEntity {
    public RedstoneConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_CONVERTER.get(), pos, state);
    }

    private int currentSignal = 0;
    private int currentSuppliedSignal = 0;

    public int getCurrentSignal() {
        return currentSignal;
    }

    public int getCurrentSuppliedSignal() {
        return currentSuppliedSignal;
    }

    @Override
    public void updateTransformers(Map<BlockPos, TransformerBlock.TransformerType> transformers) {
        super.updateTransformers(transformers);

        if (transformers.values().stream().anyMatch(transformerType -> (
                        transformerType == TransformerBlock.TransformerType.STEPDOWN || transformerType == TransformerBlock.TransformerType.STEPUP
                )
        )) {
            if (level != null) {
                Vec3 center = worldPosition.getCenter();
                level.explode(null, center.x, center.y, center.z, 4f, Level.ExplosionInteraction.BLOCK);
            }
        }
        updateAllSignals();
    }

    public void supplySignal(int strength) {
        if (currentSuppliedSignal == strength) return;
        currentSuppliedSignal = strength;
        updateAllSignals();
    }

    private void updateAllSignals() {
        AtomicInteger maxSuppliedSignal = new AtomicInteger();
        if (level != null) {
            getTransformers().forEach((blockPos, transformerType) -> {
                if (transformerType.equals(TransformerBlock.TransformerType.REDSTONE)) {
                    BlockEntity blockEntity = level.getBlockEntity(blockPos);
                    if (blockEntity instanceof RedstoneConverterBlockEntity) {
                        int signal = ((RedstoneConverterBlockEntity) blockEntity).getCurrentSuppliedSignal();
                        if (signal > maxSuppliedSignal.get()) {
                            maxSuppliedSignal.set(signal);
                        }
                    }
                }
            });

            getTransformers().forEach((blockPos, transformerType) -> {
                if (transformerType.equals(TransformerBlock.TransformerType.REDSTONE)) {
                    BlockEntity blockEntity = level.getBlockEntity(blockPos);
                    if (blockEntity instanceof RedstoneConverterBlockEntity) {
                        ((RedstoneConverterBlockEntity) blockEntity).receiveSignal(maxSuppliedSignal.get());
                    }
                }
            });
        }
    }

    public void receiveSignal(int strength) {
        if (level == null) return;
        currentSignal = strength;

        BlockPos pos = getBlockPos();
        BlockState state = level.getBlockState(pos);

        level.updateNeighborsAt(pos, state.getBlock());

        if (state.is(ModBlocks.REDSTONE_CONVERTER.get())) {
            BlockPos neighborPos = TransformerBlock.getFacingPos(pos, state);
            level.updateNeighborsAt(neighborPos, level.getBlockState(neighborPos).getBlock());
        }
    }
} 