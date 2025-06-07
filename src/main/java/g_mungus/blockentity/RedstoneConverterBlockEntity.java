package g_mungus.blockentity;

import g_mungus.block.ModBlocks;
import g_mungus.block.cableNetwork.core.Channels;
import g_mungus.block.cableNetwork.core.NetworkNode;
import g_mungus.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RedstoneConverterBlockEntity extends NetworkTerminal {
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
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);

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
            getTerminals(Channels.MAIN).forEach(node -> {
                BlockEntity blockEntity = level.getBlockEntity(node.pos());
                if (blockEntity instanceof RedstoneConverterBlockEntity redstoneConverter) {
                    int signal = redstoneConverter.getCurrentSuppliedSignal();
                    if (signal > maxSuppliedSignal.get()) {
                        maxSuppliedSignal.set(signal);
                    }
                }

            });

            getTerminals(Channels.MAIN).forEach(node -> {
                BlockEntity blockEntity = level.getBlockEntity(node.pos());
                if (blockEntity instanceof RedstoneConverterBlockEntity) {
                    ((RedstoneConverterBlockEntity) blockEntity).receiveSignal(maxSuppliedSignal.get());
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