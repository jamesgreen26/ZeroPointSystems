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

import java.util.HashMap;
import java.util.Map;

public class PowerDrillItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation BASE_MODEL = ZPSMod.resource("item/power_drill_base");
    public static final ResourceLocation HEAD_MODEL = ZPSMod.resource("item/power_drill_head");

    private static final float WRENCH_PASSIVE_SPIN_SPEED = 0.5f;
    private static final float BOOSTED_SPIN_SPEED = 36f;
    private static final float BOOST_DURATION_TICKS = 8f;
    private static final float SPEED_UP_TRANSITION_TICKS = 4f;
    private static final float SLOW_DOWN_TRANSITION_TICKS = 40f;
    private static final float STALE_STATE_TICKS = 20 * 60;
    private static final float STATE_CLEANUP_INTERVAL_TICKS = 20 * 10;

    private final RandomSource random = RandomSource.create();
    private final Map<Long, DrillRenderState> renderStates = new HashMap<>();
    private float lastStateCleanupTime = Float.NaN;

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

        DrillRenderState renderState = isAnimatedContext(displayContext) ? getRenderState(stack) : null;
        if (renderState != null) {
            renderState.update(stack, minecraft, this);
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(renderState == null ? 0.0F : renderState.spinAngle));
        renderModel(head, stack, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    public float getBoostProgress(ItemStack stack, Minecraft minecraft) {
        DrillRenderState renderState = getRenderState(stack);
        renderState.update(stack, minecraft, this);
        return renderState.boostProgress;
    }

    private DrillRenderState getRenderState(ItemStack stack) {
        return renderStates.computeIfAbsent(PowerDrillItem.getOrCreateDrillId(stack), ignored -> new DrillRenderState());
    }

    private void cleanupStaleStates(float renderTime) {
        if (!Float.isNaN(lastStateCleanupTime) && renderTime - lastStateCleanupTime < STATE_CLEANUP_INTERVAL_TICKS) {
            return;
        }

        lastStateCleanupTime = renderTime;
        renderStates.values().removeIf(state -> renderTime - state.lastUpdateTime > STALE_STATE_TICKS);
    }

    private static boolean isAnimatedContext(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static class DrillRenderState {
        private float spinAngle;
        private float spinSpeed;
        private float boostProgress;
        private float lastRenderTime = Float.NaN;
        private float lastUpdateTime = Float.NaN;

        private void update(ItemStack stack, Minecraft minecraft, PowerDrillItemRenderer renderer) {
            float renderTime = getRenderTime(minecraft);
            renderer.cleanupStaleStates(renderTime);
            lastUpdateTime = renderTime;
            float targetSpeed = getTargetSpinSpeed(stack, minecraft, renderTime);
            float targetBoostProgress = targetSpeed == BOOSTED_SPIN_SPEED ? 1.0F : 0.0F;
            if (Float.isNaN(lastRenderTime)) {
                spinSpeed = targetSpeed;
                boostProgress = targetBoostProgress;
                lastRenderTime = renderTime;
            }

            float elapsedTicks = Math.max(0, renderTime - lastRenderTime);
            lastRenderTime = renderTime;

            float transitionTicks = targetSpeed > spinSpeed ? SPEED_UP_TRANSITION_TICKS : SLOW_DOWN_TRANSITION_TICKS;
            float transition = elapsedTicks <= 0 ? 0 : 1 - (float) Math.exp(-elapsedTicks / transitionTicks);
            spinSpeed = Mth.lerp(transition, spinSpeed, targetSpeed);
            float boostTransition = elapsedTicks <= 0 ? 0 : 1 - (float) Math.exp(-elapsedTicks / SPEED_UP_TRANSITION_TICKS);
            boostProgress = Mth.lerp(boostTransition, boostProgress, targetBoostProgress);
            spinAngle = (spinAngle + spinSpeed * elapsedTicks) % 360f;
        }
    }

    private static float getTargetSpinSpeed(ItemStack stack, Minecraft minecraft, float renderTime) {
        long lastPoweredUseTick = PowerDrillItem.getLastPoweredUseTick(stack);
        if (minecraft.level != null && renderTime - lastPoweredUseTick <= BOOST_DURATION_TICKS) {
            return BOOSTED_SPIN_SPEED;
        }

        if (!PowerDrillItem.hasEnergyForBlock(stack)) {
            return 0;
        }

        return WRENCH_PASSIVE_SPIN_SPEED;
    }

    private static float getRenderTime(Minecraft minecraft) {
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
