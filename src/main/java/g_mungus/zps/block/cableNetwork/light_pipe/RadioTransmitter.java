package g_mungus.zps.block.cableNetwork.light_pipe;

import g_mungus.zps.blockentity.light_pipe.RadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber
public class RadioTransmitter extends AbstractRadioBlock {
    public RadioTransmitter(Properties arg) {
        super(arg);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RadioTransmitterBlockEntity transmitter) {
                transmitter.clearTransmission();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Scheduled by the block entity when the pipe goes blank, to end the transmission a little later. */
    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos,
                        @NotNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof RadioTransmitterBlockEntity transmitter) {
            transmitter.tickPendingClear();
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos arg, BlockState arg2) {
        return new RadioTransmitterBlockEntity(arg, arg2);
    }
}

