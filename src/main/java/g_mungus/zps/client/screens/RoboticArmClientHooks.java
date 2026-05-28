package g_mungus.zps.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class RoboticArmClientHooks {
    public static void openRoboticArmScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof g_mungus.zps.blockentity.RoboticArmBlockEntity)) return;
        minecraft.setScreen(new RoboticArmScreen(pos));
    }
}
