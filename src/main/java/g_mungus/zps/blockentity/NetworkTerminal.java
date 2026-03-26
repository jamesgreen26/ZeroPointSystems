package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface NetworkTerminal {
    Map<Integer, List<NetworkNode>> getTerminalHolder();
    BlockPos getWorldPos();

    default void defineTerminals(List<NetworkNode> terminals, int channel) {
        this.getTerminalHolder().put(channel, terminals.stream().map(node ->
            new NetworkNode(
                node.pos().subtract(getWorldPos()),
                node.channel(),
                node.terminal())
            ).toList()
        );
    }

    default List<NetworkNode> getTerminals(int channel) {
        List<NetworkNode> nodes = getTerminalHolder().get(channel);
        if (nodes != null) {
            return nodes.stream().map(node ->
                    new NetworkNode(
                            node.pos().offset(getWorldPos()),
                            node.channel(),
                            node.terminal()
                    )
            ).toList();
        } else {
            return List.of();
        }
    }

    default void loadNetwork(CompoundTag tag) {
        var terminals = getTerminalHolder();
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

    default void saveNetwork(CompoundTag tag) {
        ListTag terminalsList = new ListTag();
        getTerminalHolder().forEach((channel, nodes) -> {
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

    static @NotNull CompoundTag transformTag(CompoundTag tag, Mirror mirror, Rotation rotation) {
        CompoundTag modifiedTag = tag.copy();

        if (modifiedTag.contains("Terminals", Tag.TAG_LIST)) {
            ListTag terminalsList = modifiedTag.getList("Terminals", Tag.TAG_COMPOUND);

            for (int i = 0; i < terminalsList.size(); i++) {
                CompoundTag terminalTag = terminalsList.getCompound(i);
                ListTag nodesList = terminalTag.getList("Nodes", Tag.TAG_COMPOUND);

                for (int j = 0; j < nodesList.size(); j++) {
                    CompoundTag nodeTag = nodesList.getCompound(j);

                    BlockPos originalPos = new BlockPos(
                            nodeTag.getInt("X"),
                            nodeTag.getInt("Y"),
                            nodeTag.getInt("Z")
                    );

                    // Apply mirror then rotation (same order as structure placement)
                    BlockPos transformed = StructureTemplate.transform(
                            originalPos,
                            mirror,
                            rotation,
                            BlockPos.ZERO
                    );

                    nodeTag.putInt("X", transformed.getX());
                    nodeTag.putInt("Y", transformed.getY());
                    nodeTag.putInt("Z", transformed.getZ());
                }
            }
        }

        return modifiedTag;
    }
}
