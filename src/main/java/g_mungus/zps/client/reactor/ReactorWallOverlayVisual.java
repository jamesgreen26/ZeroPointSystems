package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import g_mungus.zps.block.reactor.ReactorGasWallBlock;
import g_mungus.zps.blockentity.reactor.ExhaustPortBlockEntity;
import g_mungus.zps.blockentity.reactor.FuelInjectorBlockEntity;
import g_mungus.zps.blockentity.reactor.ReactorGasWallBlockEntity;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

/**
 * Draws a block's indicator overlay on its outer face, tinted by the redstone level it receives:
 * dark when unpowered, up to a full glow at fifteen. The level arrives with the block entity's
 * update packet, so the visual polls it once a tick and only touches the instance when it moves.
 */
public class ReactorWallOverlayVisual<T extends ReactorGasWallBlockEntity> extends AbstractBlockEntityVisual<T>
        implements SimpleTickableVisual {

    private final TransformedInstance overlay;
    private int lastLevel = -1;

    public static ReactorWallOverlayVisual<FuelInjectorBlockEntity> fuelInjector(
            VisualizationContext ctx, FuelInjectorBlockEntity blockEntity, float partialTick) {
        return new ReactorWallOverlayVisual<>(ctx, blockEntity, partialTick, ReactorWallOverlays.FUEL_INJECTOR);
    }

    public static ReactorWallOverlayVisual<ExhaustPortBlockEntity> exhaustPort(
            VisualizationContext ctx, ExhaustPortBlockEntity blockEntity, float partialTick) {
        return new ReactorWallOverlayVisual<>(ctx, blockEntity, partialTick, ReactorWallOverlays.EXHAUST_PORT);
    }

    private ReactorWallOverlayVisual(VisualizationContext ctx, T blockEntity, float partialTick, Model model) {
        super(ctx, blockEntity, partialTick);

        overlay = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, model)
                .createInstance();

        orient(ReactorGasWallBlock.facing(blockState));
        tint(blockEntity.getRedstoneLevel());
    }

    @Override
    public void tick(TickableVisual.Context ctx) {
        tint(blockEntity.getRedstoneLevel());
    }

    private void orient(Direction facing) {
        overlay.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5f, 0.5f, 0.5f)
                .rotate(ReactorWallOverlays.rotationFor(facing))
                .translate(-0.5f, -0.5f, -0.5f)
                .setChanged();
    }

    private void tint(int level) {
        if (level == lastLevel) {
            return;
        }
        lastLevel = level;
        overlay.colorRgb(ReactorWallOverlays.tintRgb(level)).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        // Unlit material: the tint is the whole story.
    }

    @Override
    protected void _delete() {
        overlay.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(overlay);
    }
}
