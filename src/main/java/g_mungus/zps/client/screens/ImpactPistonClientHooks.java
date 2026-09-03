package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.ImpactPistonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class ImpactPistonClientHooks {
    public static void openImpactPistonScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof ImpactPistonBlockEntity)) return;
        minecraft.setScreen(new ImpactPistonScreen(pos));
    }
}
