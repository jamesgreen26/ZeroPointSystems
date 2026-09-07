package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class GasGaugeClientHooks {
    public static void openScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof GasGaugeBlockEntity)) return;
        minecraft.setScreen(new GasGaugeScreen(pos));
    }
}
