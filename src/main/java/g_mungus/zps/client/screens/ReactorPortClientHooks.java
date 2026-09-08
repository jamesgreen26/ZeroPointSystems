package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ReactorPortClientHooks {
    public static void openScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof ReactorPortBlockEntity)) return;
        minecraft.setScreen(new ReactorPortScreen(pos));
    }
}
