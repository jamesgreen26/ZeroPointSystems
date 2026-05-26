package g_mungus.zps.item;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.screens.AddressPadNameScreen;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderLevelStageEvent;
public class AddressPadClientHooks {
    private static final double LABEL_RADIUS = 64.0;
    private static final double LABEL_RADIUS_SQ = LABEL_RADIUS * LABEL_RADIUS;

    public static void openNameEntryScreen(net.minecraft.world.InteractionHand hand, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(new AddressPadNameScreen(hand, pos));
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        ItemStack stack = getHeldAddressPad(player);
        if (stack.isEmpty()) return;

        CompoundTag positions = AddressPadItem.getPositions(stack);
        if (positions.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        var camera = event.getCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        for (String name : positions.getAllKeys()) {
            if (!positions.contains(name, Tag.TAG_LONG)) continue;

            BlockPos pos = BlockPos.of(positions.getLong(name));
            double centerX = pos.getX() + 0.5;
            double centerY = pos.getY() + 1.3;
            double centerZ = pos.getZ() + 0.5;

            double dx = centerX - player.getX();
            double dy = centerY - player.getY();
            double dz = centerZ - player.getZ();
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > LABEL_RADIUS_SQ) continue;

            Outliner.getInstance()
                    .showAABB("address_pad_" + player.getUUID() + "_" + pos.asLong(), new net.minecraft.world.phys.AABB(pos))
                    .colored(0x00FFFF)
                    .lineWidth(1 / 16f);

            float alpha = 1.0f - Mth.clamp((float) Math.sqrt(distanceSq) / (float) LABEL_RADIUS, 0f, 1f);
            if (alpha <= 0.02f) continue;

            int textAlpha = ((int) (alpha * 255) << 24) | 0x00FFFFFF;
            int bgAlpha = ((int) (alpha * 90) << 24);

            poseStack.pushPose();
            poseStack.translate(centerX - camX, centerY - camY, centerZ - camZ);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());

            float scale = 0.025f;
            poseStack.scale(-scale, -scale, scale);

            float x = -font.width(name) / 2f;
            font.drawInBatch(
                    name,
                    x,
                    0,
                    textAlpha,
                    false,
                    poseStack.last().pose(),
                    buffer,
                    Font.DisplayMode.SEE_THROUGH,
                    bgAlpha,
                    0x00F000F0
            );
            font.drawInBatch(
                    name,
                    x,
                    0,
                    textAlpha,
                    false,
                    poseStack.last().pose(),
                    buffer,
                    Font.DisplayMode.NORMAL,
                    0,
                    0x00F000F0
            );
            poseStack.popPose();
        }

        buffer.endBatch();
    }

    private static ItemStack getHeldAddressPad(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof AddressPadItem) return mainHand;

        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof AddressPadItem) return offHand;

        return ItemStack.EMPTY;
    }
}
