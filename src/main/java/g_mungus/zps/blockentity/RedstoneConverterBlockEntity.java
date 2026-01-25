package g_mungus.zps.blockentity;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class RedstoneConverterBlockEntity extends NetworkTerminalImpl implements RedstoneSendingTerminal, RedstoneReceivingTerminal {
    public RedstoneConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_CONVERTER.get(), pos, state);
    }

    private int currentSignal = 0;
    private int currentSuppliedSignal = 0;

    private long lastUpdate = 0L;

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

    @Override
    public void receiveSignal(int strength, int channel) {
        if (level == null) return;
        BlockPos pos = getBlockPos();
        int oldSignal = level.getBestNeighborSignal(pos);

        currentSignal = strength;

        BlockState state = level.getBlockState(pos);

        long now = level.getGameTime();

        if (currentSignal != oldSignal || now > lastUpdate) {
            lastUpdate = now;
            if (state.is(ModBlocks.REDSTONE_CONVERTER.get())) {

                level.updateNeighborsAt(pos, state.getBlock());
                BlockPos neighborPos = TransformerBlock.getFacingPos(pos, state);
                level.updateNeighborsAt(neighborPos, level.getBlockState(neighborPos).getBlock());
            }
        }

    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("CurrentSignal", this.currentSignal);
        tag.putInt("CurrentSuppliedSignal", this.currentSuppliedSignal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("CurrentSignal")) {
            this.currentSignal = tag.getInt("CurrentSignal");
        }
        if (tag.contains("CurrentSuppliedSignal")) {
            this.currentSuppliedSignal = tag.getInt("CurrentSuppliedSignal");
        }
    }
} 