package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptComparator;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.NetworkTerminalImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScriptComparatorBlockEntity extends NetworkTerminalImpl implements LightPipeDataReceiver.Text {
    public ScriptComparatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRIPT_COMPARATOR.get(), pos, state);
    }

    private String textA = "";
    private String textB = "";

    @Override
    public void acceptText(int channel, String message) {
        if (channel == Channels.PAIR_A) {
            if (!message.equals(textA)) {
                textA = message;
                updateState();
            }
        } else if (channel == Channels.PAIR_B) {
            if (!message.equals(textB)) {
                textB = message;
                updateState();
            }
        }
    }

    public void updateState() {
        if (level instanceof ServerLevel serverLevel) {
            BlockState existingState = serverLevel.getBlockState(getBlockPos());
            if (existingState.is(ModBlocks.SCRIPT_COMPARATOR.get())) {
                boolean isPowered = existingState.getValue(ScriptComparator.POWERED);
                boolean shouldBePowered = textA.trim().equals(textB.trim());
                if (isPowered != shouldBePowered) {
                    serverLevel.setBlock(getBlockPos(), existingState.setValue(ScriptComparator.POWERED, shouldBePowered), Block.UPDATE_ALL);
                    serverLevel.updateNeighborsAt(getBlockPos(), existingState.getBlock());
                }
            }
        }
    }

    @Override
    public int getMaxLength() {
        return 144;
    }

    @Override
    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        super.defineTerminals(terminals, channel);
        if (level != null) {
            if (!hasSender(level, channel)) {
                acceptText(channel, "");
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("textA", textA);
        tag.putString("textB", textB);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        textA = tag.getString("textA");
        textB = tag.getString("textB");
    }
}
