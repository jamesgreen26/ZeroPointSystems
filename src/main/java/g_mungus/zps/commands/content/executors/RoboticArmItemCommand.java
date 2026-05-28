package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RoboticArmItemCommand {

    public static int getItems(ServerLevel serverLevel, BlockPos armPos, BlockPos targetPos) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(armPos);
        if (blockEntity instanceof RoboticArmBlockEntity roboticArm) {
            return roboticArm.RetrieveItemsFrom(targetPos) ? 1 : 0;
        }
        return 0;
    }

    public static int putItems(ServerLevel serverLevel, BlockPos armPos, BlockPos targetPos) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(armPos);
        if (blockEntity instanceof RoboticArmBlockEntity roboticArm) {
            return roboticArm.DepositItemsAt(targetPos) ? 1 : 0;
        }
        return 0;
    }
}
