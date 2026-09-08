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
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Draws a block's indicator overlay on its outer face, tinted by the redstone level it receives:
 * dark when unpowered, up to a full glow at fifteen. The level arrives with the block entity's
 * update packet, so the visual polls it once a tick and only touches the instance when it moves.
 */
public class ReactorWallOverlayVisual<T extends ReactorGasWallBlockEntity> extends AbstractBlockEntityVisual<T>
        implements SimpleTickableVisual {

    /** Tint at a redstone level of zero and of {@link ReactorGasWallBlockEntity#MAX_REDSTONE_LEVEL}. */
    private static final int OFF_COLOR = 0x431111;
    private static final int ON_COLOR = 0xFF3B2E;

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

    /**
     * Turns the north-face quad to the block's outer face with the same rotation the blockstate
     * applies to the block model, so the overlay lines up pixel for pixel with the face art.
     */
    private void orient(Direction facing) {
        // Blockstate (x, y) rotations per facing; vanilla applies them as rotateYXZ(-y, -x, 0).
        float x = switch (facing) {
            case DOWN -> 90f;
            case UP -> 270f;
            default -> 0f;
        };
        float y = switch (facing) {
            case EAST -> 90f;
            case SOUTH, UP -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
        overlay.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5f, 0.5f, 0.5f)
                .rotateYDegrees(-y)
                .rotateXDegrees(-x)
                .translate(-0.5f, -0.5f, -0.5f)
                .setChanged();
    }

    private void tint(int level) {
        if (level == lastLevel) {
            return;
        }
        lastLevel = level;
        float t = (float) level / ReactorGasWallBlockEntity.MAX_REDSTONE_LEVEL;
        overlay.color(lerpChannel(t, 16), lerpChannel(t, 8), lerpChannel(t, 0))
                .setChanged();
    }

    private static int lerpChannel(float t, int shift) {
        int off = (OFF_COLOR >> shift) & 0xFF;
        int on = (ON_COLOR >> shift) & 0xFF;
        return Mth.lerpInt(t, off, on);
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
