package g_mungus.zps.client.renderer;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import g_mungus.zps.blockentity.ImpactPistonBlockEntity;

import java.util.function.Consumer;

/**
 * Slides the Impact Piston's rod along Y. The offset comes straight from the block entity's synced
 * stroke state, so a resting piston re-submits nothing frame to frame.
 */
public class ImpactPistonVisual extends AbstractBlockEntityVisual<ImpactPistonBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance rod;
    private float lastOffset = Float.NaN;

    public ImpactPistonVisual(VisualizationContext ctx, ImpactPistonBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        rod = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(ZPSPartialModels.IMPACT_PISTON_ROD))
                .createInstance();

        animate(blockEntity.getRodOffset(partialTick));
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        animate(blockEntity.getRodOffset(ctx.partialTick()));
    }

    private void animate(float offset) {
        if (offset == lastOffset) {
            return;
        }
        lastOffset = offset;
        rod.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.0f, offset * ImpactPistonBlockEntity.ROD_TRAVEL, 0.0f)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(rod);
    }

    @Override
    protected void _delete() {
        rod.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(rod);
    }
}
