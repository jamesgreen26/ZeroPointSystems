package g_mungus.zps.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class StepDownTransformerBlockEntity extends NetworkTerminal {

    public StepDownTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEPDOWN_TRANSFORMER.get(), pos, state);
    }
} 