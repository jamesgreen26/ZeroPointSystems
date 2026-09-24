package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import g_mungus.zps.block.reactor.ReactorPortBlock;
import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * Draws a port's indicator overlay on its outer face, tinted by the redstone level it receives:
 * dark when unpowered, up to a full glow at fifteen. The level arrives with the block entity's
 * update packet and the mode with the block state, so the visual polls both once a tick and only
 * touches the instance when one moves. A mode change swaps the instance for one on the other
 * mode's model, since an instance belongs to the instancer it was made by.
 */
public class ReactorWallOverlayVisual extends AbstractBlockEntityVisual<ReactorPortBlockEntity>
        implements SimpleTickableVisual {

    private TransformedInstance overlay;
    private ReactorPortMode mode;
    private int lastLevel = -1;

    public ReactorWallOverlayVisual(VisualizationContext ctx, ReactorPortBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        createOverlay(blockState);
    }

    @Override
    public void tick(TickableVisual.Context ctx) {
        BlockState state = blockEntity.getBlockState();
        if (ReactorPortBlock.mode(state) != mode) {
            overlay.delete();
            createOverlay(state);
        }
        tint(blockEntity.getRedstoneLevel());
    }

    private void createOverlay(BlockState state) {
        mode = ReactorPortBlock.mode(state);
        overlay = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, ReactorWallOverlays.modelFor(mode))
                .createInstance();
        lastLevel = -1;
        orient(ReactorPortBlock.facing(state));
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
