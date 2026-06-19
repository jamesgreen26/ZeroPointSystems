package g_mungus.zps.item;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.zps.client.renderer.ZPSSpecialTextures;
import g_mungus.zps.client.screens.AddressPadListScreen;
import g_mungus.zps.client.screens.AddressPadNameScreen;
import g_mungus.zps.compat.ClientCompat;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collections;

public class AddressPadClientHooks {
    private static final double LABEL_RADIUS = 64.0;
    private static final double LABEL_RADIUS_SQ = LABEL_RADIUS * LABEL_RADIUS;
    private static final double LABEL_HEIGHT_ABOVE_CENTER = 0.8;

    public static void openAddressPadScreen(net.minecraft.world.InteractionHand hand, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AddressPadItem)) return;

        if (!AddressPadItem.hasSpaceFor(stack, pos)) {
            minecraft.setScreen(new AddressPadListScreen(hand));
            return;
        }
        minecraft.setScreen(new AddressPadNameScreen(hand, pos));
    }

    public static void openAddressPadListScreen(net.minecraft.world.InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof AddressPadItem)) return;
        minecraft.setScreen(new AddressPadListScreen(hand));
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
            // Stored positions may be in a VS shipyard; gauge distance and draw the label in world space.
            Vec3 worldCenter = ClientCompat.toWorldRenderPos(minecraft.level, Vec3.atCenterOf(pos));

            double distanceSq = worldCenter.distanceToSqr(player.position());
            if (distanceSq > LABEL_RADIUS_SQ) continue;

            Outliner.getInstance()
                    .showCluster("address_pad_" + player.getUUID() + "_" + pos.asLong(), Collections.singletonList(pos))
                    .colored(0x00FFFF)
                    .disableCull()
                    .lineWidth(1 / 16f);

            float alpha = 1.0f - Mth.clamp((float) Math.sqrt(distanceSq) / (float) LABEL_RADIUS, 0f, 1f);
            if (alpha <= 0.02f) continue;

            int textAlpha = ((int) (alpha * 255) << 24) | 0x00FFFFFF;
            int bgAlpha = ((int) (alpha * 90) << 24);

            poseStack.pushPose();
            poseStack.translate(worldCenter.x - camX, worldCenter.y + LABEL_HEIGHT_ABOVE_CENTER - camY, worldCenter.z - camZ);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());

            float scale = 0.025f;
            poseStack.scale(scale, -scale, scale);

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
