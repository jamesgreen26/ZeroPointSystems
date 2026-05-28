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
        PlanePoint firstJoint2d = solveFirstJoint(hand2d, segmentLength);
        PlanePoint secondJoint2d = solveSecondJoint(firstJoint2d, hand2d, segmentLength);
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
        return start.lerp(end, progress);
    }

    private static Vec3 toLocalCenter(net.minecraft.core.BlockPos origin, net.minecraft.core.BlockPos target) {
        return new Vec3(
                (target.getX() - origin.getX()) + 0.5,
                (target.getY() - origin.getY()) + 0.5,
                (target.getZ() - origin.getZ()) + 0.5
        );
    }

    private static PlanePoint solveFirstJoint(PlanePoint hand, float segmentLength) {
        double distance = Math.sqrt((hand.r * hand.r) + (hand.y * hand.y));
        if (distance < EPSILON) {
            return new PlanePoint(0.0, segmentLength);
        }

        PlanePoint preferred = new PlanePoint(-segmentLength, 0.0);
        if (distance(preferred, hand) <= (segmentLength * 2.0) + EPSILON) {
            return preferred;
        }

        // Constrained to a vertical plane (pitch-only hinges): choose the first segment angle
        // closest to "away" (theta=pi) that still allows the remaining two segments to reach.
        double phi = Math.atan2(hand.y, hand.r);
        double k = ((hand.r * hand.r) + (hand.y * hand.y) - (3.0 * segmentLength * segmentLength))
                / (2.0 * segmentLength);
        double normalized = clamp(k / distance, -1.0, 1.0);
        double delta = Math.acos(normalized);
        double thetaA = normalizeAngle(phi + delta);
        double thetaB = normalizeAngle(phi - delta);
        double away = Math.PI;
        double theta = angleDistance(thetaA, away) <= angleDistance(thetaB, away) ? thetaA : thetaB;

        return new PlanePoint(segmentLength * Math.cos(theta), segmentLength * Math.sin(theta));
    }

    private static PlanePoint solveSecondJoint(PlanePoint firstJoint, PlanePoint hand, float segmentLength) {
        PlanePoint toHand = hand.subtract(firstJoint);
        double distance = Math.sqrt((toHand.r * toHand.r) + (toHand.y * toHand.y));
        if (distance < EPSILON) {
            return firstJoint.add(0.0, segmentLength);
        }

        double clampedDistance = Math.min(distance, segmentLength * 2.0);
        double midDistance = clampedDistance * 0.5;
        double perpendicular = Math.sqrt(Math.max(0.0, (segmentLength * segmentLength) - (midDistance * midDistance)));
        double ux = toHand.r / distance;
        double uy = toHand.y / distance;

        PlanePoint midpoint = firstJoint.add(ux * midDistance, uy * midDistance);
        PlanePoint candidateA = midpoint.add(-uy * perpendicular, ux * perpendicular);
        PlanePoint candidateB = midpoint.add(uy * perpendicular, -ux * perpendicular);
        return candidateA.y >= candidateB.y ? candidateA : candidateB;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double normalizeAngle(double angle) {
        while (angle <= -Math.PI) angle += Math.PI * 2.0;
        while (angle > Math.PI) angle -= Math.PI * 2.0;
        return angle;
    }

    private static double angleDistance(double a, double b) {
        return Math.abs(normalizeAngle(a - b));
    }

    private static double distance(PlanePoint a, PlanePoint b) {
        double dr = a.r - b.r;
        double dy = a.y - b.y;
        return Math.sqrt((dr * dr) + (dy * dy));
    }

    private record PlanePoint(double r, double y) {
        private PlanePoint add(double dr, double dy) {
            return new PlanePoint(r + dr, y + dy);
        }

        private PlanePoint subtract(PlanePoint other) {
            return new PlanePoint(r - other.r, y - other.y);
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
