package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.light_pipe.DataCombinator;
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
            textA = message;
            refreshOutput(true);
        } else if (channel == Channels.TRIPLE_B) {
            textB = message;
            refreshOutput(true);
        }
    }

    public void refreshOutput() {
        refreshOutput(false);
    }

    public void refreshOutput(boolean forceSignalUpdate) {
        String combined = computeCombined();
        if (!combined.equals(outputText) || forceSignalUpdate) {
            outputText = combined;
            if (level != null) {
                updateSignal(level);
            }
        }
    }

    private String computeCombined() {
        if (getBlockState().hasProperty(DataCombinator.MODE)) {
            DataCombinator.CombineMode mode = getBlockState().getValue(DataCombinator.MODE);
            if (mode == DataCombinator.CombineMode.replace) {
                if (textA.isEmpty()) return "";
                if (textB.isEmpty()) return textA;
                int idx = textA.indexOf("%s");
                if (idx < 0) return textA;
                return textA.substring(0, idx) + textB + textA.substring(idx + 2);
            }
        }

        if (textA.isEmpty() && textB.isEmpty()) return "";
        if (textA.isEmpty()) return textB;
        if (textB.isEmpty()) return textA;
        return textA + textB;
    }

    @Override
    public void updateSignal(Level level) {
        for (NetworkNode terminal : getTerminals(Channels.TRIPLE_C)) {
            BlockEntity be = level.getBlockEntity(terminal.pos());
            int channel = terminal.channel();
            if (be instanceof LightPipeDataReceiver.Text receiver && !(receiver instanceof DataCombinatorBlockEntity)) {
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
            if (terminal.pos().equals(getBlockPos())) continue;
            BlockEntity be = level.getBlockEntity(terminal.pos());
            if (be instanceof LightPipeDataSender) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("textA", textA);
        tag.putString("textB", textB);
        tag.putString("outputText", outputText);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        textA = tag.getString("textA");
        textB = tag.getString("textB");
        outputText = tag.getString("outputText");
    }
}
