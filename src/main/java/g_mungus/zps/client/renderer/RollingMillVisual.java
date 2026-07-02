package g_mungus.zps.client.renderer;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import g_mungus.zps.block.RollingMillBlock;
import g_mungus.zps.blockentity.RollingMillBlockEntity;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

/**
 * Spins the two rolling wheels while the mill is actively processing. Each wheel sits in
 * the upper/lower slot and spins around the block-local front/back axis; rotation only
 * advances when {@link RollingMillBlockEntity#isWorking()} is true, so idle mills sit still.
 */
public class RollingMillVisual extends AbstractBlockEntityVisual<RollingMillBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance lowerWheel;
    private final TransformedInstance upperWheel;
    private final TransformedInstance[] instances;

    public RollingMillVisual(VisualizationContext ctx, RollingMillBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        lowerWheel = createDisc();
        upperWheel = createDisc();
        instances = new TransformedInstance[]{lowerWheel, upperWheel};

        animate(blockEntity.advanceRenderSpin(partialTick));
    }

    private TransformedInstance createDisc() {
        return instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(ZPSPartialModels.ROLLING_MILL_ROLLER))
                .createInstance();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        animate(blockEntity.advanceRenderSpin(ctx.partialTick()));
    }

    private void animate(float angle) {
        // Rotate the whole layout 90 deg when the mill faces along X so the wheel axes track the block's facing.
        float facingYaw = blockEntity.getBlockState().getValue(RollingMillBlock.FACING).getAxis() == Direction.Axis.X ? 90.0f : 0.0f;

        // Two wheels stacked in the upper/lower slots, counter-rotating about the block-local Z axis.
        applyDisc(lowerWheel, 0.5f, 0.25f, 0.5f, facingYaw, angle);
        applyDisc(upperWheel, 0.5f, 0.75f, 0.5f, facingYaw, -angle);
    }

    private void applyDisc(TransformedInstance instance, float cx, float cy, float cz, float facingYaw, float angle) {
        instance.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5f, 0.5f, 0.5f)    // to block centre
                .rotateYDegrees(facingYaw)      // orient the side positions to facing
                .translate(-0.5f, -0.5f, -0.5f) // back to unrotated block corner
                .translate(cx, cy, cz)          // move wheel centre to its staggered side position
                .rotateZDegrees(angle)          // spin about the block-local front/back axis
                .rotateXDegrees(90.0f)          // OBJ axis (Y) -> block-local Z
                .translate(0.0f, -0.5f, 0.0f)   // OBJ centre (0,0.5,0) -> origin
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
