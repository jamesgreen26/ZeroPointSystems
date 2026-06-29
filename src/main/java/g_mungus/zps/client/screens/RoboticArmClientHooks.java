package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public class RoboticArmClientHooks {
    public static void openRoboticArmScreen(BlockPos pos, @Nullable BlockPos motorPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(ContraptionScreenAccessClient.getBlockEntity(motorPos, pos) instanceof RoboticArmBlockEntity)) return;
        minecraft.setScreen(new RoboticArmScreen(pos, motorPos));
    }
}
