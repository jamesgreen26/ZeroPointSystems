package g_mungus.zps.client.renderer;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Renders the arm's swivel base and segments when the Flywheel backend is active.
 * The held item and debug overlays remain in {@link RoboticArmBlockEntityRenderer}.
 */
public class RoboticArmVisual extends AbstractBlockEntityVisual<RoboticArmBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance swivelBase;
    private final TransformedInstance lowerSegment;
    private final TransformedInstance middleSegment;
    private final TransformedInstance upperSegment;
    private final TransformedInstance[] instances;

    private Vec3 lastHandPos;

    public RoboticArmVisual(VisualizationContext ctx, RoboticArmBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        swivelBase = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(ZPSPartialModels.ROBOTIC_ARM_SWIVEL_BASE))
                .createInstance();
        lowerSegment = createSegmentInstance();
        middleSegment = createSegmentInstance();
        upperSegment = createSegmentInstance();
        instances = new TransformedInstance[]{swivelBase, lowerSegment, middleSegment, upperSegment};

        animate(partialTick);
    }

    private TransformedInstance createSegmentInstance() {
        return instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(ZPSPartialModels.ROBOTIC_ARM_SEGMENT))
                .createInstance();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        animate(ctx.partialTick());
    }

    private void animate(float partialTick) {
        RoboticArmKinematics.ArmPose pose = RoboticArmKinematics.solve(blockEntity, partialTick);
        if (!blockEntity.isMoving() && pose.handPos().equals(lastHandPos)) return;
        lastHandPos = pose.handPos();

        // Transforms mirror RoboticArmBlockEntityRenderer, including the trailing
        // (-0.5, -0.5, -0.5) that ItemRenderer applies implicitly for context NONE.
        swivelBase.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5f, 0.5f, 0.5f)
                .rotateYDegrees(-pose.swivelYawDegrees())
                .translate(-0.5f, -0.5f, -0.5f)
                .setChanged();

        transformSegment(lowerSegment, RoboticArmKinematics.BASE_POS, pose.firstJoint(), pose);
        transformSegment(middleSegment, pose.firstJoint(), pose.secondJoint(), pose);
        transformSegment(upperSegment, pose.secondJoint(), pose.handPos(), pose);
    }

    private void transformSegment(TransformedInstance instance, Vec3 start, Vec3 end, RoboticArmKinematics.ArmPose pose) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < RoboticArmKinematics.EPSILON) {
            instance.setZeroTransform().setChanged();
            return;
        }

        double radialComponent = direction.dot(pose.radialAxis());
        float hingeDegrees = (float) Math.toDegrees(Math.atan2(radialComponent, direction.y));
        instance.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(start)
                .rotateYDegrees(-pose.swivelYawDegrees())
                .rotateZDegrees(-hingeDegrees)
                .translate(-0.5f, 0.0f, -0.5f)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(instances);
    }

    @Override
    protected void _delete() {
        for (TransformedInstance instance : instances) {
            instance.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (TransformedInstance instance : instances) {
            consumer.accept(instance);
        }
    }
}
