package g_mungus.blockentity;

import g_mungus.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NetworkTerminal extends BlockEntity {

    public NetworkTerminal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private final Map<Integer, List<NetworkNode>> terminals = new ConcurrentHashMap<>();

    public void defineTerminals(List<NetworkNode> terminals, int channel) {
        this.terminals.put(channel, terminals.stream().map(node ->
            new NetworkNode(
                node.pos().subtract(this.worldPosition),
                node.channel(),
                node.terminal())
            ).toList()
        );
    }

    public List<NetworkNode>  getTerminals(int channel) {
        return terminals.get(channel).stream().map(node ->
            new NetworkNode(
                node.pos().offset(this.worldPosition),
                node.channel(),
                node.terminal()
            )
        ).toList();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        terminals.clear();
        
        if (tag.contains("Terminals", Tag.TAG_LIST)) {
            ListTag terminalsList = tag.getList("Terminals", Tag.TAG_COMPOUND);
            for (int i = 0; i < terminalsList.size(); i++) {
                CompoundTag terminalTag = terminalsList.getCompound(i);
                int channel = terminalTag.getInt("Channel");
                
                ListTag nodesList = terminalTag.getList("Nodes", Tag.TAG_COMPOUND);
                List<NetworkNode> nodes = nodesList.stream()
                    .map(nodeTag -> {
                        CompoundTag nodeCompound = (CompoundTag) nodeTag;
                        BlockPos pos = new BlockPos(
                            nodeCompound.getInt("X"),
                            nodeCompound.getInt("Y"),
                            nodeCompound.getInt("Z")
                        );
                        int nodeChannel = nodeCompound.getInt("Channel");
                        boolean isTerminal = nodeCompound.getBoolean("IsTerminal");
                        return new NetworkNode(pos, nodeChannel, isTerminal);
                    })
                    .toList();
                
                terminals.put(channel, nodes);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        
        ListTag terminalsList = new ListTag();
        terminals.forEach((channel, nodes) -> {
            CompoundTag terminalTag = new CompoundTag();
            terminalTag.putInt("Channel", channel);
            
            ListTag nodesList = new ListTag();
            nodes.forEach(node -> {
                CompoundTag nodeTag = new CompoundTag();
                nodeTag.putInt("X", node.pos().getX());
                nodeTag.putInt("Y", node.pos().getY());
                nodeTag.putInt("Z", node.pos().getZ());
                nodeTag.putInt("Channel", node.channel());
                nodeTag.putBoolean("IsTerminal", node.terminal());
                nodesList.add(nodeTag);
            });
            
            terminalTag.put("Nodes", nodesList);
            terminalsList.add(terminalTag);
        });
        
        tag.put("Terminals", terminalsList);
    }
}
