package g_mungus.zps.client.renderer;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.blockentity.RedstoneBandSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * Draws a block's redstone band, tinted by its redstone level: dark at zero, up to bright red at
 * fifteen, and lit by the world like the rest of the block. The level arrives with the block entity's update packet and the facing and cladding
 * with the block state, so the visual polls them once a tick and only touches the instance when
 * one moves.
 */
public class RedstoneBandVisual<T extends BlockEntity & RedstoneBandSource> extends AbstractBlockEntityVisual<T>
        implements SimpleTickableVisual {

    private final TransformedInstance band;
    private BlockState lastState;
    private int lastLevel = -1;

    public RedstoneBandVisual(VisualizationContext ctx, T blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        band = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, RedstoneBand.modelFor(blockEntity))
                .createInstance();
        place(blockState);
        tint(blockEntity.getBandLevel());
    }

    @Override
    public void tick(TickableVisual.Context ctx) {
        BlockState state = blockEntity.getBlockState();
        if (state != lastState) {
            place(state);
        }
        tint(blockEntity.getBandLevel());
    }

    private void place(BlockState state) {
        lastState = state;
        if (!RedstoneBand.isVisible(state)) {
            band.setZeroTransform().setChanged();
            return;
        }
        band.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5f, 0.5f, 0.5f)
                .rotate(RedstoneBand.rotationFor(state.getValue(TransformerBlock.FACING)))
                .translate(-0.5f, -0.5f, -0.5f)
                .setChanged();
    }

    private void tint(int level) {
        if (level == lastLevel) {
            return;
        }
        lastLevel = level;
        band.colorRgb(RedstoneBand.tintRgb(level)).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        // The band is on the block's own plate, which lets light in, so its own light is the right one.
        relight(band);
    }

    @Override
    protected void _delete() {
        band.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(band);
    }
}
