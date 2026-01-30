package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractTextDataReceiver extends NetworkTerminalImpl implements LightPipeDataReceiver.Text {
    public AbstractTextDataReceiver(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected String currentDisplayText = "";

    @Override
    public void acceptText(int channel, String message) {
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
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) {
            if (!hasSender(level)) {
                acceptText(channel, "");
            }
        }
    }

    protected boolean hasSender(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.MAIN)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataSender) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("DisplayText", currentDisplayText);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        currentDisplayText = tag.getString("DisplayText");
    }
}
