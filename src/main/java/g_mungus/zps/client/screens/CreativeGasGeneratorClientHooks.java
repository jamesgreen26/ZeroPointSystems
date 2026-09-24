package g_mungus.zps.client.screens;

import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public class CreativeGasGeneratorClientHooks {
    public static void openScreen(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!(minecraft.level.getBlockEntity(pos) instanceof CreativeGasGeneratorBlockEntity)) return;
        minecraft.setScreen(new CreativeGasGeneratorScreen(pos));
    }
}
