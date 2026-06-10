package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.item.ModItems;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RoboticArmBlockEntityRenderer implements BlockEntityRenderer<RoboticArmBlockEntity> {
    private static final ItemStack SEGMENT_MODEL_STACK = new ItemStack(ModItems.ROBOTIC_ARM.get());
    private static final ModelResourceLocation SEGMENT_BER_MODEL =
            new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "robotic_arm_segment"), "inventory");
    private static final ModelResourceLocation SWIVEL_BASE_BER_MODEL =
            new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "robotic_arm_swivel_base"), "inventory");
    private static final List<BlockPos> RANGE_VOLUME_OFFSETS = buildRangeVolumeOffsets();
    private final ItemRenderer itemRenderer;

    public RoboticArmBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(RoboticArmBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderDebugTargetBox(blockEntity, poseStack, bufferSource);

        if (blockEntity.isViewRange()) {
            showRangeOutline(Outliner.getInstance(), blockEntity.getBlockPos());
        }

        // RoboticArmVisual renders the arm geometry when Flywheel is active
        boolean flywheelActive = VisualizationManager.supportsVisualization(blockEntity.getLevel());
        if (flywheelActive && blockEntity.getHeldStack().isEmpty()) return;

        RoboticArmKinematics.ArmPose pose = RoboticArmKinematics.solve(blockEntity, partialTick);

        if (!flywheelActive) {
            BakedModel segmentModel = Minecraft.getInstance().getModelManager().getModel(SEGMENT_BER_MODEL);
            BakedModel swivelBaseModel = Minecraft.getInstance().getModelManager().getModel(SWIVEL_BASE_BER_MODEL);

            renderSwivelBaseModel(pose.radialAxis(), swivelBaseModel, poseStack, bufferSource, packedLight, packedOverlay);
            renderSegmentModel(RoboticArmKinematics.BASE_POS, pose.firstJoint(), pose.radialAxis(), segmentModel, poseStack, bufferSource, packedLight, packedOverlay);
            renderSegmentModel(pose.firstJoint(), pose.secondJoint(), pose.radialAxis(), segmentModel, poseStack, bufferSource, packedLight, packedOverlay);
            renderSegmentModel(pose.secondJoint(), pose.handPos(), pose.radialAxis(), segmentModel, poseStack, bufferSource, packedLight, packedOverlay);
        }

        renderHeldItem(blockEntity, pose.handPos(), poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void renderDebugTargetBox(RoboticArmBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;

        BlockPos origin = blockEntity.getBlockPos();
        BlockPos target = blockEntity.getCurrentTargetBlockPos();
        Vec3 localCenter = RoboticArmKinematics.toLocalCenter(origin, target);
        double minX = localCenter.x - 0.5;
        double minY = localCenter.y - 0.5;
        double minZ = localCenter.z - 0.5;
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                1.0f,
                blockEntity.isMoving() ? 0.2f : 1.0f,
                0.2f,
                1.0f
        );
    }

    private void renderSwivelBaseModel(Vec3 radialAxis, BakedModel model,
                                       PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, int packedOverlay) {
        float swivelYawDegrees = (float) Math.toDegrees(Math.atan2(radialAxis.z, radialAxis.x));
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-swivelYawDegrees));
        itemRenderer.render(
                SEGMENT_MODEL_STACK,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                model
        );
        poseStack.popPose();
    }

    private void renderSegmentModel(Vec3 start, Vec3 end, Vec3 radialAxis, BakedModel model,
                                    PoseStack poseStack, MultiBufferSource bufferSource,
                                    int packedLight, int packedOverlay) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < RoboticArmKinematics.EPSILON) return;

        double radialComponent = direction.dot(radialAxis);
        double verticalComponent = direction.y;
        float swivelYawDegrees = (float) Math.toDegrees(Math.atan2(radialAxis.z, radialAxis.x));
        float hingeDegrees = (float) Math.toDegrees(Math.atan2(radialComponent, verticalComponent));

        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-swivelYawDegrees));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-hingeDegrees));
        poseStack.translate(0.0F, 0.5F, 0.0F);
        itemRenderer.render(
                SEGMENT_MODEL_STACK,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                model
        );
        poseStack.popPose();
    }

    private void renderHeldItem(RoboticArmBlockEntity blockEntity, Vec3 handPos, PoseStack poseStack,
                                MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack heldStack = blockEntity.getHeldStack();
        if (heldStack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(handPos.x, handPos.y, handPos.z);
        Vec3 fromBase = handPos.subtract(RoboticArmKinematics.BASE_POS);
        if ((fromBase.x * fromBase.x) + (fromBase.z * fromBase.z) > RoboticArmKinematics.EPSILON) {
            float yawDegrees = (float) Math.toDegrees(Math.atan2(fromBase.z, fromBase.x));
            poseStack.mulPose(Axis.YP.rotationDegrees(-yawDegrees + 90.0f));
        }
        poseStack.scale(0.55f, 0.55f, 0.55f);
        itemRenderer.renderStatic(
                heldStack,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                (int) blockEntity.getBlockPos().asLong()
        );
        poseStack.popPose();
    }

    /** Draws the arm's reach volume as a cyan checkered cluster onto the given outliner. */
    public static void showRangeOutline(Outliner outliner, BlockPos origin) {
        List<BlockPos> volumePositions = new ArrayList<>(RANGE_VOLUME_OFFSETS.size());
        for (BlockPos offset : RANGE_VOLUME_OFFSETS) {
            volumePositions.add(origin.offset(offset));
        }
        outliner
                .showCluster("robotic_arm_range_" + origin.asLong(), volumePositions)
                .colored(0x00FFFF)
                .withFaceTextures(ZPSSpecialTextures.CHECKERED, ZPSSpecialTextures.HIGHLIGHT_CHECKERED)
                .disableCull()
                .lineWidth(1 / 16f);
    }

    private static List<BlockPos> buildRangeVolumeOffsets() {
        List<BlockPos> result = new ArrayList<>();
        int max = RoboticArmBlockEntity.MAX_DISTANCE_BLOCKS;
        int maxSq = max * max;
        for (int x = -max; x <= max; x++) {
            for (int y = -max; y <= max; y++) {
                for (int z = -max; z <= max; z++) {
                    BlockPos offset = new BlockPos(x, y, z);
                    if (offset.distSqr(BlockPos.ZERO) > maxSq) continue;
                    result.add(offset);
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public boolean shouldRenderOffScreen(RoboticArmBlockEntity p_112306_) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRender(RoboticArmBlockEntity blockEntity, Vec3 cameraPos) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPos, getViewDistance());
    }
}
