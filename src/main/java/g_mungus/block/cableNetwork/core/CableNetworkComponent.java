package g_mungus.block.cableNetwork.core;

import g_mungus.ZPSMod;
import g_mungus.blockentity.NetworkTerminal;
import g_mungus.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public interface CableNetworkComponent {


    default void updateNetwork(BlockPos pos, Level level) {
        for (int initialChannel = Channels.getInitialChannel(getTotalChannelCount()); initialChannel <= Channels.getFinalChannel(getTotalChannelCount()); initialChannel++) {
            Queue<NetworkNode> toCheck = new ArrayDeque<>();
            List<NetworkNode> checked = new ArrayList<>();
            List<NetworkNode> terminals = new ArrayList<>();

            toCheck.add(new NetworkNode(pos, initialChannel, this.isTerminal()));

            while (!toCheck.isEmpty()) {
                NetworkNode current = toCheck.poll();

                if (checked.contains(current)) continue;
                checked.add(current);

                if (current.terminal()) {
                    terminals.add(current);
                }

                BlockState blockState = level.getBlockState(current.pos());
                Block block = blockState.getBlock();

                if (block instanceof CableNetworkComponent component) {
                    toCheck.addAll(component.getConnectedNodes(current, level, checked));
                }
            }

            terminals.forEach(terminalNode -> {
                BlockEntity blockEntity = level.getBlockEntity(terminalNode.pos());

                if (blockEntity instanceof NetworkTerminal terminal) {
                    terminal.defineTerminals(terminals, terminalNode.channel());
                }
            });
        }
    }

    boolean isTerminal();

    int getTotalChannelCount();

    int getChannelCountForConnection(BlockPos self, BlockPos from, Level level);

    default List<NetworkNode> getConnectedNodes(NetworkNode self, Level level, List<NetworkNode> exclude) {
        List<NetworkNode> output = new ArrayList<>();
        List<BlockPos> neighbors = getConnectingNeighbors(self, level);
        neighbors.forEach(neighbor -> {
            CableNetworkComponent component = getConnectedComponent(self.pos(), neighbor, level);
            if (component != null) {
                NetworkNode node = component.getNode(neighbor, self, level);
                if (!exclude.contains(node)) {
                    output.add(node);
                }
            }
        });
        return output;
    }

    List<BlockPos> getConnectingNeighbors(NetworkNode self, Level level);

    default @Nullable CableNetworkComponent getConnectedComponent(BlockPos self, BlockPos from, Level level) {
        Block blockProspect = level.getBlockState(from).getBlock();
        if (blockProspect instanceof CableNetworkComponent component &&
                getChannelCountForConnection(self, from, level) == component.getChannelCountForConnection(from, self, level) && getChannelCountForConnection(self, from, level) > 0
        ) {
            return component;
        } else {
            return null;
        }
    }

    default boolean canConnect(BlockPos self, BlockPos from, Level level) { return (getConnectedComponent(self, from, level) != null); }

    default NetworkNode getNode(BlockPos self, NetworkNode input, Level level) {
        int channel = getNewChannel(self, input, level);
        return new NetworkNode(self, channel, isTerminal());
    }

    int getNewChannel(BlockPos self, NetworkNode input, Level level);
}
