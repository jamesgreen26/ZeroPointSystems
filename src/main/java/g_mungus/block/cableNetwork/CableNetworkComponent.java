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
        Queue<Pair<BlockPos, BlockPos>> toCheck = new ArrayDeque<>();
        List<BlockPos> checked = new ArrayList<>();
        Map<BlockPos, TransformerBlock.TransformerType> transformers = new HashMap<>();

        toCheck.add(new Pair<>(pos, null));

        while (!toCheck.isEmpty()) {
            Pair<BlockPos, BlockPos> current = toCheck.poll();
            if (checked.contains(current.getFirst())) continue;
            checked.add(current.getFirst());

            Block block = level.getBlockState(current.getFirst()).getBlock();
            if (block instanceof TransformerBlock) {
                TransformerBlock.TransformerType type = ((TransformerBlock) block).getTransformerType();
                transformers.put(current.getFirst(), type);
            }

            if (block instanceof CableNetworkComponent) {
                ((CableNetworkComponent) block).getConnectedPositions(level, current.component1(), current.component2()).forEach((key, value) -> {
                    toCheck.add(new Pair<>(key, value));
                });
            }
        }

        transformers.keySet().forEach(blockPos -> {
            BlockEntity blockEntity = level.getBlockEntity(blockPos);

            if (blockEntity instanceof TransformerBlockEntity) {
                ((TransformerBlockEntity) blockEntity).updateTransformers(transformers);
            }
        });
    }

    Map<BlockPos, BlockPos> getConnectedPositions(Level level, BlockPos selfPos, BlockPos from);
}
