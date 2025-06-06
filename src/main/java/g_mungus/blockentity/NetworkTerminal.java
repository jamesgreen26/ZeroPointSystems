package g_mungus.blockentity;

import g_mungus.block.cableNetwork.core.NetworkNode;
import g_mungus.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NetworkTerminal extends BlockEntity {

    public NetworkTerminal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private final List<NetworkNode> terminals = new ArrayList<>();

    public void defineTerminals(List<NetworkNode> terminals) {
        this.terminals.clear();

        terminals.forEach(terminal -> {
            BlockPos relativePos = terminal.pos().subtract(this.worldPosition);
            this.terminals.add(new NetworkNode(relativePos, terminal.channel(), terminal.terminal()));
        });
    }

    public List<NetworkNode>  getTerminals() {
        List<NetworkNode>  worldTransformers = new ArrayList<>();
        terminals.forEach(node -> {
            // Convert relative position back to world position
            BlockPos worldPos = node.pos().offset(this.worldPosition);
            worldTransformers.add(new NetworkNode(worldPos, node.channel(), node.terminal()));
        });
        return worldTransformers;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag terminals = new CompoundTag();
        this.terminals.forEach(node -> {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", node.pos().getX());
            posTag.putInt("y", node.pos().getY());
            posTag.putInt("z", node.pos().getZ());
            posTag.putInt("channel", node.channel());
            terminals.put(node.pos().toString(), posTag);
        });
        tag.put("terminals", terminals);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        terminals.clear();
        if (tag.contains("terminals")) {
            CompoundTag terminalsTag = tag.getCompound("terminals");
            for (String key : terminalsTag.getAllKeys()) {
                CompoundTag posTag = terminalsTag.getCompound(key);
                BlockPos pos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
                );
                int channel = posTag.getInt("channel");
                terminals.add(new NetworkNode(pos, channel, true));
            }
        }
    }
}
