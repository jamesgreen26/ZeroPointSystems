package g_mungus.block.cableNetwork;

import g_mungus.blockentity.TransformerBlockEntity;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public interface CableNetworkComponent extends CanConnectCables {

    default void updateNetwork(BlockPos pos, Level level) {
        Queue<ConnectionAdjacency> toCheck = new ArrayDeque<>();
        List<BlockPos> checked = new ArrayList<>();
        List<TerminalConnection> transformers = new ArrayList<>();

        toCheck.add(new ConnectionAdjacency(pos, null, -1));

        while (!toCheck.isEmpty()) {
            ConnectionAdjacency current = toCheck.poll();
            if (checked.contains(current.getFirst())) continue;
            checked.add(current.getFirst());

            Block block = level.getBlockState(current.getFirst()).getBlock();
            if (block instanceof TransformerBlock) {
                TransformerBlock.TransformerType type = ((TransformerBlock) block).getTransformerType();
                transformers.add(new TerminalConnection(current.getFirst(), type, -1));
            }

            if (block instanceof CableNetworkComponent) {
                toCheck.addAll(((CableNetworkComponent) block).getConnectedPositions(level, current.getFirst(), current.getSecond()));
            }
        }

        transformers.forEach(transformer -> {
            BlockEntity blockEntity = level.getBlockEntity(transformer.pos);

            if (blockEntity instanceof TransformerBlockEntity) {
                ((TransformerBlockEntity) blockEntity).updateTransformers(transformers);
            }
        });
    }

    List<ConnectionAdjacency> getConnectedPositions(Level level, BlockPos selfPos, BlockPos from);

    record TerminalConnection(BlockPos pos, TransformerBlock.TransformerType type, int channelForController) { }
}
