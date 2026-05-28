package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class RoboticArmBlockEntityRenderer implements BlockEntityRenderer<RoboticArmBlockEntity> {
    private static final Vec3 BASE_POS = new Vec3(0.5, 0.5, 0.5);
    private static final float ARM_R = 0.8f;
    private static final float ARM_G = 0.8f;
    private static final float ARM_B = 0.85f;
    private static final float ARM_A = 1.0f;
    private static final float SEGMENT_COUNT = 3.0f;
    private static final float EPSILON = 1.0e-4f;
    private static final double SWIVEL_ANGLE_MIN = 0.05;
    private static final double SWIVEL_PITCH_MAX = 0.18;

    public RoboticArmBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RoboticArmBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Vec3 handPos = getInterpolatedHandPosition(blockEntity, partialTick);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        float segmentLength = RoboticArmBlockEntity.MAX_DISTANCE_BLOCKS / SEGMENT_COUNT;

        ArmPlane armPlane = ArmPlane.from(BASE_POS, handPos);
        PlanePoint hand2d = armPlane.toPlane(handPos);
        ArmSolution solution2d = solveArmInPlane(hand2d, segmentLength);
        PlanePoint firstJoint2d = solution2d.firstJoint();
        PlanePoint secondJoint2d = solution2d.secondJoint();
        Vec3 firstJoint = armPlane.toWorld(firstJoint2d);
        Vec3 secondJoint = armPlane.toWorld(secondJoint2d);

        drawSegmentLine(poseStack, lines, BASE_POS, firstJoint, ARM_R, ARM_G, ARM_B, ARM_A);
        drawSegmentLine(poseStack, lines, firstJoint, secondJoint, ARM_R, ARM_G, ARM_B, ARM_A);
        drawSegmentLine(poseStack, lines, secondJoint, handPos, ARM_R, ARM_G, ARM_B, ARM_A);

        drawJointBox(poseStack, lines, BASE_POS, 0.08f, 0.5f, 0.9f, 1.0f, 1.0f);
        drawJointBox(poseStack, lines, handPos, 0.10f, 1.0f, 0.7f, 0.2f, 1.0f);
    }

    private static Vec3 getInterpolatedHandPosition(RoboticArmBlockEntity blockEntity, float partialTick) {
        Vec3 settled = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getHandBlockPos());
        if (!blockEntity.isMoving() || blockEntity.getLevel() == null) return settled;

        double elapsed = (blockEntity.getLevel().getGameTime() - blockEntity.getMoveStartTick()) + partialTick;
        double progress = Math.min(1.0, Math.max(0.0, elapsed / RoboticArmBlockEntity.MOVE_TIME_TICKS));
        Vec3 start = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getMoveStartBlockPos());
        Vec3 end = toLocalCenter(blockEntity.getBlockPos(), blockEntity.getMoveTargetBlockPos());
        return interpolateHandSwivel(start, end, progress);
    }

    private static Vec3 interpolateHandSwivel(Vec3 start, Vec3 end, double progress) {
        Vec3 startLocal = start.subtract(BASE_POS);
        Vec3 endLocal = end.subtract(BASE_POS);

        double startHorizontal = Math.sqrt((startLocal.x * startLocal.x) + (startLocal.z * startLocal.z));
        double endHorizontal = Math.sqrt((endLocal.x * endLocal.x) + (endLocal.z * endLocal.z));
        if (startHorizontal < EPSILON || endHorizontal < EPSILON) return start.lerp(end, progress);

        double startAzimuth = Math.atan2(startLocal.z, startLocal.x);
        double endAzimuth = Math.atan2(endLocal.z, endLocal.x);
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
        return new ArmSolution(first, second);
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

    private static double wrapRadians(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2.0;
        while (angle < -Math.PI) angle += Math.PI * 2.0;
        return angle;
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
        private static ArmPlane from(Vec3 base, Vec3 hand) {
            Vec3 horizontal = new Vec3(hand.x - base.x, 0.0, hand.z - base.z);
            if (horizontal.lengthSqr() < EPSILON) {
                return new ArmPlane(base, new Vec3(1.0, 0.0, 0.0));
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

    private static void drawSegmentLine(PoseStack poseStack, VertexConsumer lines, Vec3 start, Vec3 end,
                                        float r, float g, float b, float a) {
        Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < EPSILON) return;
        Vec3 normal = direction.normalize();
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;

        lines.vertex(poseMatrix, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, a).normal(normalMatrix, nx, ny, nz).endVertex();
        lines.vertex(poseMatrix, (float) end.x, (float) end.y, (float) end.z).color(r, g, b, a).normal(normalMatrix, nx, ny, nz).endVertex();
    }

    private static void drawJointBox(PoseStack poseStack, VertexConsumer lines, Vec3 center, float halfSize, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                center.x - halfSize,
                center.y - halfSize,
                center.z - halfSize,
                center.x + halfSize,
                center.y + halfSize,
                center.z + halfSize,
                r,
                g,
                b,
                a
        );
    }

    @Override
    public boolean shouldRenderOffScreen(RoboticArmBlockEntity p_112306_) {
        return true;
    }

    @Override
    public boolean shouldRender(RoboticArmBlockEntity p_173568_, Vec3 p_173569_) {
        return true;
    }
}
