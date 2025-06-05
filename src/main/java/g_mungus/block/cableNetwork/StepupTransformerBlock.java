package g_mungus.block.cableNetwork;

import g_mungus.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class StepupTransformerBlock extends TransformerBlock {
    @Override
    public TransformerType getTransformerType() {
        return TransformerType.STEPUP;
    }

    public StepupTransformerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.STEPUP_TRANSFORMER.get().create(pos, state);
    }
}
