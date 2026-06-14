package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.PowerDrillItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;

public class PowerDrillItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation BASE_MODEL = ZPSMod.resource("item/power_drill_base");
    public static final ResourceLocation HEAD_MODEL = ZPSMod.resource("item/power_drill_head");

    private static final float WRENCH_PASSIVE_SPIN_SPEED = 0.5f;
    private static final float BOOSTED_SPIN_SPEED = 36f;
    private static final float BOOST_DURATION_TICKS = 8f;
    private static final float SPEED_UP_TRANSITION_TICKS = 4f;
    private static final float SLOW_DOWN_TRANSITION_TICKS = 40f;

    private final RandomSource random = RandomSource.create();
    private float spinAngle;
    private float spinSpeed;
    private float lastRenderTime = Float.NaN;

    public PowerDrillItemRenderer() {
        super(null, null);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel base = minecraft.getModelManager().getModel(BASE_MODEL);
        BakedModel head = minecraft.getModelManager().getModel(HEAD_MODEL);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);

        renderModel(base, stack, poseStack, bufferSource, packedLight, packedOverlay);

        float renderTime = getRenderTime(minecraft);
        updateSpin(stack, minecraft, renderTime);

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinAngle));
        renderModel(head, stack, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    private void updateSpin(ItemStack stack, Minecraft minecraft, float renderTime) {
        float targetSpeed = getTargetSpinSpeed(stack, minecraft, renderTime);
        if (Float.isNaN(lastRenderTime)) {
            spinSpeed = targetSpeed;
            lastRenderTime = renderTime;
        }

        float elapsedTicks = Math.max(0, renderTime - lastRenderTime);
        lastRenderTime = renderTime;

        float transitionTicks = targetSpeed > spinSpeed ? SPEED_UP_TRANSITION_TICKS : SLOW_DOWN_TRANSITION_TICKS;
        float transition = elapsedTicks <= 0 ? 0 : 1 - (float) Math.exp(-elapsedTicks / transitionTicks);
        spinSpeed = Mth.lerp(transition, spinSpeed, targetSpeed);
        spinAngle = (spinAngle + spinSpeed * elapsedTicks) % 360f;
    }

    private float getTargetSpinSpeed(ItemStack stack, Minecraft minecraft, float renderTime) {
        long lastPoweredUseTick = PowerDrillItem.getLastPoweredUseTick(stack);
        if (minecraft.level != null && renderTime - lastPoweredUseTick <= BOOST_DURATION_TICKS) {
            return BOOSTED_SPIN_SPEED;
        }

        if (!PowerDrillItem.hasEnergyForBlock(stack)) {
            return 0;
        }

        return WRENCH_PASSIVE_SPIN_SPEED;
    }

    private float getRenderTime(Minecraft minecraft) {
        return minecraft.level == null ? Util.getMillis() / 50f : minecraft.level.getGameTime() + minecraft.getFrameTime();
    }

    private void renderModel(BakedModel model, ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource,
                             int packedLight, int packedOverlay) {
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(bufferSource, renderType, true, stack.hasFoil());
                renderQuads(pass, stack, poseStack, consumer, renderType, itemRenderer, packedLight, packedOverlay);
            }
        }

        poseStack.popPose();
    }

    private void renderQuads(BakedModel model, ItemStack stack, PoseStack poseStack, VertexConsumer consumer,
                             RenderType renderType, ItemRenderer itemRenderer, int packedLight, int packedOverlay) {
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            itemRenderer.renderQuadList(
                    poseStack,
                    consumer,
                    model.getQuads(null, direction, random, ModelData.EMPTY, renderType),
                    stack,
                    packedLight,
                    packedOverlay
            );
        }

        random.setSeed(42L);
        itemRenderer.renderQuadList(
                poseStack,
                consumer,
                model.getQuads(null, null, random, ModelData.EMPTY, renderType),
                stack,
                packedLight,
                packedOverlay
        );
    }
}
