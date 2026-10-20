package g_mungus.zps.client;

import g_mungus.zps.block.LaunchPlatformLayout;
import g_mungus.zps.block.ModBlocks;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/** While the player crouches and looks at a Launch Platform, outlines what it would lift: white, or red if too big. */
public final class LaunchPlatformOutline {
    private static final String OUTLINE_KEY = "launch_platform_volume";
    /** The search reads a few thousand blocks, so it is redone only this often while the same platform is watched. */
    private static final int REFRESH_TICKS = 5;

    private static @Nullable BlockPos lastPos;
    private static long lastGameTime;
    private static @Nullable LaunchPlatformLayout lastLayout;

    private LaunchPlatformOutline() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !player.isShiftKeyDown()) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(ModBlocks.LAUNCH_PLATFORM.get())) return;

        long gameTime = level.getGameTime();
        if (!pos.equals(lastPos) || gameTime - lastGameTime >= REFRESH_TICKS || gameTime < lastGameTime) {
            lastPos = pos.immutable();
            lastGameTime = gameTime;
            lastLayout = LaunchPlatformLayout.find(level, pos);
        }
        LaunchPlatformLayout layout = lastLayout;
        if (layout == null) return;

        boolean tooBig = layout.tooBig();
        Outliner.getInstance()
                .showAABB(OUTLINE_KEY, layout.volumeAABB())
                .colored(tooBig ? 0xFF0000 : 0xFFFFFF)
                .lineWidth(1 / 16f);

        if (tooBig) {
            player.displayClientMessage(Component.translatable("block.zps.launch_platform.too_big",
                    layout.sizeX(), layout.height(), layout.sizeZ(),
                    LaunchPlatformLayout.MAX_WIDTH, LaunchPlatformLayout.MAX_HEIGHT, LaunchPlatformLayout.MAX_WIDTH), true);
        }
    }
}
