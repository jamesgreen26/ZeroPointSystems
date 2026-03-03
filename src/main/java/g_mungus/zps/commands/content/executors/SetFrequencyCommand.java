package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.light_pipe.RadioBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SetFrequencyCommand {

    public static int setFrequency(ServerLevel serverLevel, BlockPos pos, int frequencyIndex) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);

        if (blockEntity instanceof RadioBlockEntity radio) {
            if (frequencyIndex > -1 && frequencyIndex < 16) {
                radio.setFrequencyIndex(frequencyIndex);
                return 1;
            }
        }

        return 0;
    }
}
