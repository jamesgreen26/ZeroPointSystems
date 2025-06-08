package g_mungus.block.cableNetwork;

import g_mungus.blockentity.ModBlockEntities;
import g_mungus.blockentity.StepDownTransformerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StepdownTransformerBlock extends TransformerBlock {
    public StepdownTransformerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.STEPDOWN_TRANSFORMER.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : 
            (level1, pos, state1, blockEntity) -> {
                if (blockEntity instanceof StepDownTransformerBlockEntity) {
                    StepDownTransformerBlockEntity.tick(level1, pos, state1, (StepDownTransformerBlockEntity) blockEntity);
                }
            };
    }
}
