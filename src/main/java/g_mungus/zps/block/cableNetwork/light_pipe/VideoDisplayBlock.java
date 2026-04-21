package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.blockentity.light_pipe.VideoDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VideoDisplayBlock extends DataDisplayBlock implements EntityBlock {

    public VideoDisplayBlock(Properties arg) {
        super(arg);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos arg, @NotNull BlockState arg2) {
        return new VideoDisplayBlockEntity(arg, arg2);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState arg, Level arg2, @NotNull BlockPos arg3) {
        BlockEntity blockEntity = arg2.getBlockEntity(arg3);
        if (blockEntity instanceof VideoDisplayBlockEntity it) {
            return Math.min((int) Math.ceil((15 * ((double) it.getDisplayText().length()) / ((double) it.getMaxLength()))), 15);
        }
        return 0;
    }
}
