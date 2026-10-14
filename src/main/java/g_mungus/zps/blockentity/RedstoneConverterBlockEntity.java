package g_mungus.zps.blockentity;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RedstoneConverterBlockEntity extends NetworkTerminalImpl implements RedstoneSendingTerminal, RedstoneReceivingTerminal, RedstoneBandSource {
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

    /** The stronger of what the converter feeds the network and what the network gives it. */
    @Override
    public int getBandLevel() {
        return Math.max(currentSignal, currentSuppliedSignal);
    }

    @Override
    public boolean hasFrontWindow() {
        return true;
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);

        updateSignal(level, Channels.MAIN);
    }

    public void supplySignal(int strength) {
        if (currentSuppliedSignal == strength) return;
        currentSuppliedSignal = strength;
        syncBand();
        updateSignal(level, Channels.MAIN);
    }

    @Override
    public void receiveSignal(int strength, int channel) {
        if (level == null) return;
        BlockPos pos = getBlockPos();

        if (currentSignal != strength) {
            currentSignal = strength;
            syncBand();
        }
        BlockState state = level.getBlockState(pos);

        if (state.is(ModBlocks.REDSTONE_CONVERTER.get())) {
            level.updateNeighborsAt(pos, state.getBlock());
            BlockPos neighborPos = TransformerBlock.getFacingPos(pos, state);
            level.updateNeighborsAt(neighborPos, level.getBlockState(neighborPos).getBlock());
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("CurrentSignal", this.currentSignal);
        tag.putInt("CurrentSuppliedSignal", this.currentSuppliedSignal);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("CurrentSignal")) {
            this.currentSignal = tag.getInt("CurrentSignal");
        }
        if (tag.contains("CurrentSuppliedSignal")) {
            this.currentSuppliedSignal = tag.getInt("CurrentSuppliedSignal");
        }
    }

    private void syncBand() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Only the signals go to clients, for the band; the network is the server's business. */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CurrentSignal", currentSignal);
        tag.putInt("CurrentSuppliedSignal", currentSuppliedSignal);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        currentSignal = tag.getInt("CurrentSignal");
        currentSuppliedSignal = tag.getInt("CurrentSuppliedSignal");
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket packet) {
        handleUpdateTag(packet.getTag());
    }
}
