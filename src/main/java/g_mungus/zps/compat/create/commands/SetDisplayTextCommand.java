package g_mungus.zps.compat.create.commands;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import g_mungus.zps.compat.create.DisplayLinkManualTextAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class SetDisplayTextCommand {
    private SetDisplayTextCommand() {
    }

    public static int setDisplayText(ServerLevel serverLevel, BlockPos pos, String text) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink)
                || !(displayLink instanceof DisplayLinkManualTextAccessor accessor)) {
            return 0;
        }

        accessor.zps$setManualDisplayText(text);
        displayLink.updateGatheredData();
        return 1;
    }
}
