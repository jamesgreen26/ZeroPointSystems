package g_mungus.blockentity;

import g_mungus.block.cableNetwork.TransformerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TransformerBlockEntity extends BlockEntity {

    public TransformerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private final Map<BlockPos, TransformerBlock.TransformerType> transformers = new ConcurrentHashMap<>();

    public void updateTransformers(Map<BlockPos, TransformerBlock.TransformerType> transformers) {
        this.transformers.clear();
        transformers.forEach((pos, type) -> {
            // Convert world position to relative position
            BlockPos relativePos = pos.subtract(this.worldPosition);
            this.transformers.put(relativePos, type);
        });
    }

    public Map<BlockPos, TransformerBlock.TransformerType> getTransformers() {
        Map<BlockPos, TransformerBlock.TransformerType> worldTransformers = new ConcurrentHashMap<>();
        transformers.forEach((relativePos, type) -> {
            // Convert relative position back to world position
            BlockPos worldPos = relativePos.offset(this.worldPosition);
            worldTransformers.put(worldPos, type);
        });
        return worldTransformers;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag transformersTag = new CompoundTag();
        transformers.forEach((pos, type) -> {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            posTag.putString("type", type.name());
            transformersTag.put(pos.toString(), posTag);
        });
        tag.put("transformers", transformersTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        transformers.clear();
        if (tag.contains("transformers")) {
            CompoundTag transformersTag = tag.getCompound("transformers");
            for (String key : transformersTag.getAllKeys()) {
                CompoundTag posTag = transformersTag.getCompound(key);
                BlockPos pos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
                );
                TransformerBlock.TransformerType type = TransformerBlock.TransformerType.valueOf(posTag.getString("type"));
                transformers.put(pos, type);
            }
        }
    }
}
