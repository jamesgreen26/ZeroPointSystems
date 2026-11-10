package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.gas.GasFilterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class GasFilterClientHooks {
    public static void openScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof GasFilterBlockEntity)) return;
        minecraft.setScreen(new GasFilterScreen(pos));
    }
}
