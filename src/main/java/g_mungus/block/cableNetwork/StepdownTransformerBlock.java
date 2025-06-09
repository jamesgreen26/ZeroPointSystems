package g_mungus.block.cableNetwork;

import g_mungus.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
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
}
