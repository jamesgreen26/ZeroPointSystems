package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class SerialBusClientHooks {
    public static void openScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof SerialBusBlockEntity)) return;
        minecraft.setScreen(new SerialBusScreen(pos));
    }
}
