package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.item.ModItems;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
    private static final Vec3 BASE_POS = new Vec3(0.5, 0.5, 0.5);
    private static final float SEGMENT_COUNT = 3.0f;
    private static final float EPSILON = 1.0e-4f;
    private static final double SWIVEL_ANGLE_MIN = 0.05;
    private static final double SWIVEL_PITCH_MAX = 0.18;
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
        if (blockEntity.isViewRange()) {
            BlockPos origin = blockEntity.getBlockPos();
            List<BlockPos> volumePositions = new ArrayList<>(RANGE_VOLUME_OFFSETS.size());
            for (BlockPos offset : RANGE_VOLUME_OFFSETS) {
                volumePositions.add(origin.offset(offset));
            }
            Outliner.getInstance()
                    .showCluster("robotic_arm_range_" + origin.asLong(), volumePositions)
                    .colored(0x00FFFF)
                    .withFaceTextures(ZPSSpecialTextures.CHECKERED, ZPSSpecialTextures.HIGHLIGHT_CHECKERED)
                    .disableCull()
                    .lineWidth(1 / 16f);
        }

        Vec3 fallbackAxis = blockEntity.getLastSwivelAxis();
        Vec3 handPos = getInterpolatedHandPosition(blockEntity, partialTick, fallbackAxis);
        float segmentLength = RoboticArmBlockEntity.MAX_DISTANCE_BLOCKS / SEGMENT_COUNT;
        BakedModel segmentModel = Minecraft.getInstance().getModelManager().getModel(SEGMENT_BER_MODEL);
        BakedModel swivelBaseModel = Minecraft.getInstance().getModelManager().getModel(SWIVEL_BASE_BER_MODEL);

        ArmPlane armPlane = ArmPlane.from(BASE_POS, handPos, fallbackAxis);
        blockEntity.setLastSwivelAxis(armPlane.radialAxis());
        renderSwivelBaseModel(armPlane.radialAxis(), swivelBaseModel, poseStack, bufferSource, packedLight, packedOverlay);
        PlanePoint hand2d = armPlane.toPlane(handPos);
        ArmSolution solution2d = solveArmInPlane(hand2d, segmentLength);
        PlanePoint firstJoint2d = solution2d.firstJoint();
        PlanePoint secondJoint2d = solution2d.secondJoint();
        Vec3 firstJoint = armPlane.toWorld(firstJoint2d);
        Vec3 secondJoint = armPlane.toWorld(secondJoint2d);

        renderSegmentModel(BASE_POS, firstJoint, armPlane.radialAxis(), segmentModel, poseStack, bufferSource, packedLight, packedOverlay);
        renderSegmentModel(firstJoint, secondJoint, armPlane.radialAxis(), segmentModel, poseStack, bufferSource, packedLight, packedOverlay);
        renderSegmentModel(secondJoint, handPos, armPlane.radialAxis(), segmentModel, poseStack, bufferSource, packedLight, packedOverlay);

        renderHeldItem(blockEntity, handPos, poseStack, bufferSource, packedLight, packedOverlay);
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
        if (direction.lengthSqr() < EPSILON) return;

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
        Vec3 fromBase = handPos.subtract(BASE_POS);
        if ((fromBase.x * fromBase.x) + (fromBase.z * fromBase.z) > EPSILON) {
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

    private static Vec3 getInterpolatedHandPosition(RoboticArmBlockEntity blockEntity, float partialTick, Vec3 fallbackAxis) {
        Vec3 settled = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getHandBlockPos());
        if (!blockEntity.isMoving() || blockEntity.getLevel() == null) return settled;

        double elapsed = (blockEntity.getLevel().getGameTime() - blockEntity.getMoveStartTick()) + partialTick;
        double progress = Math.min(1.0, Math.max(0.0, elapsed / RoboticArmBlockEntity.MOVE_TIME_TICKS));
        Vec3 start = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getMoveStartBlockPos());
        Vec3 end = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getMoveTargetBlockPos());
        return interpolateHandSwivel(start, end, progress, fallbackAxis);
    }

    private static Vec3 interpolateHandSwivel(Vec3 start, Vec3 end, double progress, Vec3 fallbackAxis) {
        Vec3 startLocal = start.subtract(BASE_POS);
        Vec3 endLocal = end.subtract(BASE_POS);

        double startHorizontal = Math.sqrt((startLocal.x * startLocal.x) + (startLocal.z * startLocal.z));
        double endHorizontal = Math.sqrt((endLocal.x * endLocal.x) + (endLocal.z * endLocal.z));
        if (startHorizontal < EPSILON && endHorizontal < EPSILON) return start.lerp(end, progress);

        double fallbackAzimuth = Math.atan2(fallbackAxis.z, fallbackAxis.x);
        double startAzimuth = startHorizontal < EPSILON
                ? fallbackAzimuth
                : Math.atan2(startLocal.z, startLocal.x);
        double endAzimuth = endHorizontal < EPSILON
                ? fallbackAzimuth
                : Math.atan2(endLocal.z, endLocal.x);
        double deltaAzimuth = wrapRadians(endAzimuth - startAzimuth);
        if (Math.abs(deltaAzimuth) < SWIVEL_ANGLE_MIN) return start.lerp(end, progress);

        double azimuth = startAzimuth + (deltaAzimuth * progress);
        double horizontal = lerp(startHorizontal, endHorizontal, progress);

        double y = lerp(startLocal.y, endLocal.y, progress);
        double sweepFactor = clamp(Math.abs(deltaAzimuth) / Math.PI, 0.0, 1.0);
        double pitchNudge = Math.sin(Math.PI * progress) * SWIVEL_PITCH_MAX * sweepFactor;
        y += pitchNudge;

        double x = Math.cos(azimuth) * horizontal;
        double z = Math.sin(azimuth) * horizontal;
        return BASE_POS.add(x, y, z);
    }

    private static Vec3 toLocalCenter(net.minecraft.core.BlockPos origin, net.minecraft.core.BlockPos target) {
        return new Vec3(
                (target.getX() - origin.getX()) + 0.5,
                (target.getY() - origin.getY()) + 0.5,
                (target.getZ() - origin.getZ()) + 0.5
        );
    }

    private static ArmSolution solveArmInPlane(PlanePoint hand, float segmentLength) {
        if (length(hand) < EPSILON) {
            PlanePoint first = new PlanePoint(0.0, segmentLength);
            PlanePoint second = first.add(0.0, segmentLength);
            return new ArmSolution(first, second);
        }

        double handAngle = Math.atan2(hand.y, hand.r);
        double normalizedReach = clamp(length(hand) / segmentLength, 0.0, 3.0);

        // For equal bend at both joints:
        // d/L = |dir(a) + dir(a+b) + dir(a+2b)| = |1 + 2cos(b)|
        // Choose the forward-facing branch: 1 + 2cos(b) = d/L.
        double cosBend = clamp((normalizedReach - 1.0) * 0.5, -1.0, 1.0);
        double bend = Math.acos(cosBend);
        double firstAngle = handAngle - bend;
        double secondAngle = handAngle;

        PlanePoint first = new PlanePoint(
                segmentLength * Math.cos(firstAngle),
                segmentLength * Math.sin(firstAngle)
        );
        PlanePoint second = first.add(
                segmentLength * Math.cos(secondAngle),
                segmentLength * Math.sin(secondAngle)
        );
        PlanePoint axis = new PlanePoint(hand.r / length(hand), hand.y / length(hand));
        PlanePoint mirroredFirst = reflectAcrossAxis(first, axis);
        PlanePoint mirroredSecond = reflectAcrossAxis(second, axis);
        return new ArmSolution(mirroredFirst, mirroredSecond);
    }

    private static double length(PlanePoint point) {
        return Math.sqrt((point.r * point.r) + (point.y * point.y));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double start, double end, double t) {
        return start + ((end - start) * t);
    }

    private static PlanePoint reflectAcrossAxis(PlanePoint point, PlanePoint axisUnit) {
        double projection = (point.r * axisUnit.r) + (point.y * axisUnit.y);
        double reflectedR = (2.0 * projection * axisUnit.r) - point.r;
        double reflectedY = (2.0 * projection * axisUnit.y) - point.y;
        return new PlanePoint(reflectedR, reflectedY);
    }

    private static double wrapRadians(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2.0;
        while (angle < -Math.PI) angle += Math.PI * 2.0;
        return angle;
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

    private record PlanePoint(double r, double y) {
        private PlanePoint add(double dr, double dy) {
            return new PlanePoint(r + dr, y + dy);
        }
    }

    private record ArmSolution(PlanePoint firstJoint, PlanePoint secondJoint, double score) {
        private ArmSolution(PlanePoint firstJoint, PlanePoint secondJoint) {
            this(firstJoint, secondJoint, 1.0);
        }
    }

    private record ArmPlane(Vec3 base, Vec3 radialAxis) {
        private static ArmPlane from(Vec3 base, Vec3 hand, Vec3 fallbackAxis) {
            Vec3 horizontal = new Vec3(hand.x - base.x, 0.0, hand.z - base.z);
            if (horizontal.lengthSqr() < EPSILON) {
                if (fallbackAxis.lengthSqr() < EPSILON) {
                    return new ArmPlane(base, new Vec3(1.0, 0.0, 0.0));
                }
                return new ArmPlane(base, fallbackAxis.normalize());
            }
            return new ArmPlane(base, horizontal.normalize());
        }

        private PlanePoint toPlane(Vec3 point) {
            Vec3 local = point.subtract(base);
            return new PlanePoint(local.dot(radialAxis), local.y);
        }

        private Vec3 toWorld(PlanePoint point) {
            return base.add(radialAxis.scale(point.r)).add(0.0, point.y, 0.0);
        }
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
