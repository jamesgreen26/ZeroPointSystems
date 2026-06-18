package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.PoweredToolItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.Map;

public class PoweredToolItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation BASE_MODEL = ModelResourceLocation.standalone(ZPSMod.resource("power_drill_base"));
    public static final ModelResourceLocation HEAD_MODEL = ModelResourceLocation.standalone(ZPSMod.resource("power_drill_head"));

    private static final float PASSIVE_SPIN_SPEED = 0.5f;
    private static final float BOOSTED_SPIN_SPEED = 36f;
    private static final float BOOST_DURATION_TICKS = 8f;
    private static final float SPEED_UP_TRANSITION_TICKS = 4f;
    private static final float SLOW_DOWN_TRANSITION_TICKS = 40f;
    private static final float STALE_STATE_TICKS = 20 * 60;
    private static final float STATE_CLEANUP_INTERVAL_TICKS = 20 * 10;
    private static final double NANOS_PER_TICK = 50_000_000.0;
    private static final double GROUND_MODEL_Z_OFFSET = 4.0D / 16.0D;

    private final RandomSource random = RandomSource.create();
    private final Map<Long, ToolRenderState> renderStates = new HashMap<>();
    private final ModelResourceLocation baseModel;
    private final ModelResourceLocation headModel;
    private double lastStateCleanupTime = Double.NaN;

    public PoweredToolItemRenderer() {
        this(BASE_MODEL, HEAD_MODEL);
    }

    protected PoweredToolItemRenderer(ModelResourceLocation baseModel, ModelResourceLocation headModel) {
        super(null, null);
        this.baseModel = baseModel;
        this.headModel = headModel;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel base = minecraft.getModelManager().getModel(baseModel);
        BakedModel head = minecraft.getModelManager().getModel(headModel);

        poseStack.pushPose();
        if (displayContext == ItemDisplayContext.GROUND) {
            poseStack.translate(0.0D, 0.0D, GROUND_MODEL_Z_OFFSET);
        }

        poseStack.translate(0.5F, 0.5F, 0.5F);

        renderModel(base, stack, poseStack, bufferSource, packedLight, packedOverlay);

        ToolRenderState renderState = isAnimatedContext(displayContext) ? getRenderState(stack) : null;
        if (renderState != null) {
            renderState.updateForRender(stack, minecraft, this);
        }

        poseStack.pushPose();
        applyHeadTransform(
                poseStack,
                renderState == null ? 0.0F : (float) renderState.spinAngle,
                renderState == null ? 0.0F : renderState.boostProgress
        );
        renderModel(head, stack, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    public float getBoostProgress(ItemStack stack, Minecraft minecraft) {
        ToolRenderState renderState = getRenderState(stack);
        renderState.updateBoost(stack, minecraft, this);
        return renderState.boostProgress;
    }

    private ToolRenderState getRenderState(ItemStack stack) {
        return renderStates.computeIfAbsent(PoweredToolItem.getOrCreateToolId(stack), ignored -> new ToolRenderState());
    }

    protected void applyHeadTransform(PoseStack poseStack, float spinAngle, float boostProgress) {
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinAngle));
    }

    private void cleanupStaleStates(double renderTime) {
        if (!Double.isNaN(lastStateCleanupTime) && renderTime - lastStateCleanupTime < STATE_CLEANUP_INTERVAL_TICKS) {
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

    private static class ToolRenderState {
        private double spinAngle;
        private double spinSpeed;
        private float boostProgress;
        private double lastSpinRenderTime = Double.NaN;
        private double lastBoostRenderTime = Double.NaN;
        private double lastUpdateTime = Double.NaN;

        private void updateForRender(ItemStack stack, Minecraft minecraft, PoweredToolItemRenderer renderer) {
            double renderTime = getSmoothRenderTime();
            updateBoost(stack, minecraft, renderer, renderTime);

            float targetSpeed = getTargetSpinSpeed(stack, minecraft);
            if (Double.isNaN(lastSpinRenderTime)) {
                spinSpeed = targetSpeed;
                lastSpinRenderTime = renderTime;
            }

            double elapsedTicks = Math.max(0.0, renderTime - lastSpinRenderTime);
            lastSpinRenderTime = renderTime;

            double transitionTicks = targetSpeed > spinSpeed ? SPEED_UP_TRANSITION_TICKS : SLOW_DOWN_TRANSITION_TICKS;
            double transition = elapsedTicks <= 0.0 ? 0.0 : 1.0 - Math.exp(-elapsedTicks / transitionTicks);
            spinSpeed = Mth.lerp(transition, spinSpeed, targetSpeed);
            spinAngle = (spinAngle + spinSpeed * elapsedTicks) % 360.0;
        }

        private void updateBoost(ItemStack stack, Minecraft minecraft, PoweredToolItemRenderer renderer) {
            updateBoost(stack, minecraft, renderer, getSmoothRenderTime());
        }

        private void updateBoost(ItemStack stack, Minecraft minecraft, PoweredToolItemRenderer renderer, double renderTime) {
            lastUpdateTime = renderTime;
            renderer.cleanupStaleStates(renderTime);
            float targetSpeed = getTargetSpinSpeed(stack, minecraft);
            float targetBoostProgress = targetSpeed == BOOSTED_SPIN_SPEED ? 1.0F : 0.0F;
            if (Double.isNaN(lastBoostRenderTime)) {
                boostProgress = targetBoostProgress;
                lastBoostRenderTime = renderTime;
            }

            double elapsedTicks = Math.max(0.0, renderTime - lastBoostRenderTime);
            lastBoostRenderTime = renderTime;

            float boostTransition = elapsedTicks <= 0.0 ? 0.0F : 1.0F - (float) Math.exp(-elapsedTicks / SPEED_UP_TRANSITION_TICKS);
            boostProgress = Mth.lerp(boostTransition, boostProgress, targetBoostProgress);
        }
    }

    private static float getTargetSpinSpeed(ItemStack stack, Minecraft minecraft) {
        long lastPoweredUseTick = PoweredToolItem.getLastPoweredUseTick(stack);
        if (minecraft.level != null && getGameRenderTime(minecraft) - lastPoweredUseTick <= BOOST_DURATION_TICKS) {
            return BOOSTED_SPIN_SPEED;
        }

        if (!PoweredToolItem.hasEnergyForBlock(stack)) {
            return 0;
        }

        return PASSIVE_SPIN_SPEED;
    }

    private static double getGameRenderTime(Minecraft minecraft) {
        return minecraft.level == null ? getSmoothRenderTime() : minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaTicks();
    }

    private static double getSmoothRenderTime() {
        return Util.getNanos() / NANOS_PER_TICK;
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
