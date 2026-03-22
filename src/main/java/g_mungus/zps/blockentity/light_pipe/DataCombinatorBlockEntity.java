package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DataCombinatorBlockEntity extends NetworkTerminalImpl implements LightPipeDataReceiver.Text, LightPipeDataSender {

    private String textA = "";
    private String textB = "";
    private String outputText = "";

    public DataCombinatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_COMBINATOR.get(), pos, state);
    }

    @Override
    public void acceptText(int channel, String message) {
        if (channel == Channels.TRIPLE_A) {
            if (!message.equals(textA)) {
                textA = message;
                computeAndSend();
            }
        } else if (channel == Channels.TRIPLE_B) {
            if (!message.equals(textB)) {
                textB = message;
                computeAndSend();
            }
        }
    }

    private void computeAndSend() {
        String combined = computeCombined();
        if (!combined.equals(outputText)) {
            outputText = combined;
            if (level != null) {
                updateSignal(level);
            }
        }
    }

    private String computeCombined() {
        if (textA.isEmpty() && textB.isEmpty()) return "";
        if (textA.isEmpty()) return textB;
        if (textB.isEmpty()) return textA;
        int idx = textA.indexOf("{}");
        if (idx >= 0) {
            return textA.substring(0, idx) + textB + textA.substring(idx + 2);
        }
        return textA + textB;
    }

    @Override
    public void updateSignal(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.TRIPLE_C)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            int channel = terminal.channel();
            if (be instanceof LightPipeDataReceiver.Text receiver) {
                receiver.acceptText(channel, outputText.substring(0, Math.min(outputText.length(), receiver.getMaxLength())));
            }
        }
    }

    @Override
    public void onRemove(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.TRIPLE_C)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataReceiver.Text receiver) {
                receiver.acceptText(terminal.channel(), "");
            }
        }
    }

    @Override
    public String provideNextDisplayText(int length) {
        return "";
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) {
            if (channel == Channels.TRIPLE_A || channel == Channels.TRIPLE_B) {
                if (!hasSender(level, channel)) {
                    acceptText(channel, "");
                }
            } else if (channel == Channels.TRIPLE_C) {
                updateSignal(level);
            }
        }
    }

    private boolean hasSender(Level level, int channel) {
        for (NetworkNode terminal : getTerminals(channel)) {
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
        tag.putString("textA", textA);
        tag.putString("textB", textB);
        tag.putString("outputText", outputText);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        textA = tag.getString("textA");
        textB = tag.getString("textB");
        outputText = tag.getString("outputText");
    }
}
