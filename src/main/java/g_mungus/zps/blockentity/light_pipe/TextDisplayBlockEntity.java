package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class TextDisplayBlockEntity extends AbstractDisplayBlockEntity {
    public TextDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEXT_DISPLAY.get(), pos, state);
    }
}
