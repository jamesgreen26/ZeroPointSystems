package g_mungus.zps.client.renderer;

import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import g_mungus.zps.compat.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Per-frame IK solve for the robotic arm, shared between the immediate-mode
 * renderer and the Flywheel visual so both produce identical poses.
 */
public final class RoboticArmKinematics {
    public static final Vec3 BASE_POS = new Vec3(0.5, 0.5, 0.5);
    public static final float EPSILON = 1.0e-4f;
    private static final float SEGMENT_COUNT = 3.0f;
    public static final float SEGMENT_LENGTH = RoboticArmBlockEntity.MAX_DISTANCE_BLOCKS / SEGMENT_COUNT;
    private static final double SWIVEL_ANGLE_MIN = 0.05;
    private static final double SWIVEL_PITCH_MAX = 0.18;

    private RoboticArmKinematics() {
    }

    /**
     * Joint positions are in the arm's local block space (base block corner at the origin).
     */
    public record ArmPose(Vec3 radialAxis, float swivelYawDegrees, Vec3 firstJoint, Vec3 secondJoint, Vec3 handPos) {
    }

    /**
     * Side effect: stores the resolved radial axis back via {@code blockEntity.setLastSwivelAxis}
     * so the swivel stays smooth across frames.
     */
    public static ArmPose solve(RoboticArmBlockEntity blockEntity, float partialTick) {
        Vec3 fallbackAxis = blockEntity.getLastSwivelAxis();
        Vec3 handPos = getInterpolatedHandPosition(blockEntity, partialTick, fallbackAxis);
        ArmPlane armPlane = ArmPlane.from(BASE_POS, handPos, fallbackAxis);
        blockEntity.setLastSwivelAxis(armPlane.radialAxis());

        ArmSolution solution = solveArmInPlane(armPlane.toPlane(handPos), SEGMENT_LENGTH);
        float swivelYawDegrees = (float) Math.toDegrees(Math.atan2(armPlane.radialAxis().z, armPlane.radialAxis().x));
        return new ArmPose(
                armPlane.radialAxis(),
                swivelYawDegrees,
                armPlane.toWorld(solution.firstJoint()),
                armPlane.toWorld(solution.secondJoint()),
                handPos
        );
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

    public static Vec3 toLocalCenter(BlockPos origin, BlockPos target) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return new Vec3(
                    (target.getX() - origin.getX()) + 0.5,
                    (target.getY() - origin.getY()) + 0.5,
                    (target.getZ() - origin.getZ()) + 0.5
            );
        }
        Vec3 local = ClientCompat.toLocalRenderSpaceOf(level, origin, Vec3.atCenterOf(target));
        return local.subtract(origin.getX(), origin.getY(), origin.getZ());
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

    private record PlanePoint(double r, double y) {
        private PlanePoint add(double dr, double dy) {
            return new PlanePoint(r + dr, y + dy);
        }
    }

    private record ArmSolution(PlanePoint firstJoint, PlanePoint secondJoint) {
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
}
