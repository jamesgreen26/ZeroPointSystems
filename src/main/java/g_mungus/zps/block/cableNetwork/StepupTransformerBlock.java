package g_mungus.zps.block.cableNetwork;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.StepUpTransformerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StepupTransformerBlock extends TransformerBlock {
    public StepupTransformerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.STEPUP_TRANSFORMER.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : 
            (level1, pos, state1, blockEntity) -> {
                if (blockEntity instanceof StepUpTransformerBlockEntity) {
                    StepUpTransformerBlockEntity.tick(level1, pos, state1, (StepUpTransformerBlockEntity) blockEntity);
                }
            };
    }
}
