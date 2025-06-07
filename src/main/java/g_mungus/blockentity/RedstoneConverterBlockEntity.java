package g_mungus.blockentity;

import g_mungus.block.ModBlocks;
import g_mungus.block.cableNetwork.core.Channels;
import g_mungus.block.cableNetwork.core.NetworkNode;
import g_mungus.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RedstoneConverterBlockEntity extends NetworkTerminal implements RedstoneCapableTerminal {
    public RedstoneConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_CONVERTER.get(), pos, state);
    }

    private int currentSignal = 0;
    private int currentSuppliedSignal = 0;

    public int getCurrentSignal() {
        return currentSignal;
    }

    public int getCurrentSuppliedSignal(int channel) {
        return currentSuppliedSignal;
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);

        updateAllSignals(level, getTerminals(Channels.MAIN));
    }

    public void supplySignal(int strength) {
        if (currentSuppliedSignal == strength) return;
        currentSuppliedSignal = strength;
        updateAllSignals(level, getTerminals(Channels.MAIN));
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