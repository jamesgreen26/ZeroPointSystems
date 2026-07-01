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
 * Spins the two rolling discs while the mill is actively processing. Each disc sits flat
 * against a side face (axis left-right) and pokes slightly out of the block; rotation only
 * advances when {@link RollingMillBlockEntity#isWorking()} is true, so idle mills sit still.
 */
public class RollingMillVisual extends AbstractBlockEntityVisual<RollingMillBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance leftDisc;
    private final TransformedInstance rightDisc;
    private final TransformedInstance[] instances;

    public RollingMillVisual(VisualizationContext ctx, RollingMillBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        leftDisc = createDisc();
        rightDisc = createDisc();
        instances = new TransformedInstance[]{leftDisc, rightDisc};

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
        // The discs poke out the machine's left/right faces; rotate the whole layout 90 deg when the
        // mill faces along X so those faces track the block's facing.
        float facingYaw = blockEntity.getBlockState().getValue(RollingMillBlock.FACING).getAxis() == Direction.Axis.X ? 90.0f : 0.0f;

        // Two flat (horizontal) discs, staggered in height so they don't intersect, each poking
        // slightly out of a side face. They spin about the vertical (Y) axis, counter-rotating.
        applyDisc(leftDisc, 0.25f, 0.42f, 0.5f, facingYaw, angle);
        applyDisc(rightDisc, 0.75f, 0.58f, 0.5f, facingYaw, -angle);
    }

    private void applyDisc(TransformedInstance instance, float cx, float cy, float cz, float facingYaw, float angle) {
        instance.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5f, 0.5f, 0.5f)    // to block centre
                .rotateYDegrees(facingYaw)      // orient the side positions to facing
                .translate(-0.5f, -0.5f, -0.5f) // back to unrotated block corner
                .translate(cx, cy, cz)          // move disc centre to its staggered side position
                .rotateYDegrees(angle)          // spin about the vertical disc axis
                .translate(-0.5f, -0.5f, -0.5f) // model centre (8,8,8) -> origin
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
