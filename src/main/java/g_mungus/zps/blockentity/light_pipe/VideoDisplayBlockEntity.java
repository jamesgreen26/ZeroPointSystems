package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class VideoDisplayBlockEntity extends AbstractDisplayBlockEntity {
    public VideoDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VIDEO_DISPLAY.get(), pos, state);
    }
}
