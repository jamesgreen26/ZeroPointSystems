package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TextDisplayBlockEntity extends NetworkTerminalImpl implements LightPipeDataReceiver.Text {
    public TextDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEXT_DISPLAY.get(), pos, state);
    }

    private String currentDisplayText = "";

    @Override
    public void acceptText(String message) {
        if (!message.equals(currentDisplayText)) {
            currentDisplayText = message;
            setChanged();

            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(
                        worldPosition,
                        getBlockState(),
                        getBlockState(),
                        Block.UPDATE_CLIENTS
                );
            }
        }
    }

    @Override
    public int getMaxLength() {
        return 144;
    }

    public String getDisplayText() {
        return currentDisplayText;
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) {
            if (!hasSender(level)) {
                acceptText("");
            }
        }
    }

    private boolean hasSender(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataSender) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt != null && pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("DisplayText", currentDisplayText);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentDisplayText = tag.getString("DisplayText");
    }
}
