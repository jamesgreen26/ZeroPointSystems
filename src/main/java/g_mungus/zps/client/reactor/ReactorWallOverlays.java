package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * The indicator overlays drawn on the outer face of the Reactor Port: one full-face quad per
 * mode carrying that mode's {@code *_overlay} texture, tinted per instance by the redstone level
 * the block receives.
 *
 * <p>The quad lies on the block's north face, where the models put the outer face; the visual turns
 * it to the block's facing. It sits exactly on the face plane and relies on the material's polygon
 * offset to win the depth test against the opaque block behind it. Both models are static
 * singletons: Flywheel keys instancers by model identity, so every port in a mode shares one draw.
 */
public final class ReactorWallOverlays {

    public static final ResourceLocation FUEL_INJECTOR_TEXTURE = texture("fuel_injector");
    public static final ResourceLocation EXHAUST_PORT_TEXTURE = texture("exhaust_port");

    public static final Model FUEL_INJECTOR = overlay("fuel_injector", FUEL_INJECTOR_TEXTURE);
    public static final Model EXHAUST_PORT = overlay("exhaust_port", EXHAUST_PORT_TEXTURE);

    /** The overlay texture a port shows in a mode. */
    public static ResourceLocation textureFor(ReactorPortMode mode) {
        return mode == ReactorPortMode.OUTPUT ? EXHAUST_PORT_TEXTURE : FUEL_INJECTOR_TEXTURE;
    }

    /** The overlay model a port shows in a mode. */
    public static Model modelFor(ReactorPortMode mode) {
        return mode == ReactorPortMode.OUTPUT ? EXHAUST_PORT : FUEL_INJECTOR;
    }

    /** Tint at a redstone level of zero; the item models use it too. */
    public static final int OFF_COLOR = 0x431111;
    /** Tint at {@link ReactorPortBlockEntity#MAX_REDSTONE_LEVEL}. */
    public static final int ON_COLOR = 0xFF3B2E;

    private ReactorWallOverlays() {
    }

    /** The overlay's tint for a redstone level, as packed RGB. */
    public static int tintRgb(int level) {
        float t = (float) level / ReactorPortBlockEntity.MAX_REDSTONE_LEVEL;
        return lerpChannel(t, 16) << 16 | lerpChannel(t, 8) << 8 | lerpChannel(t, 0);
    }

    private static int lerpChannel(float t, int shift) {
        int off = (OFF_COLOR >> shift) & 0xFF;
        int on = (ON_COLOR >> shift) & 0xFF;
        return Mth.lerpInt(t, off, on);
    }

    /**
     * Turns the north-face quad to the block's outer face with the same rotation the blockstate
     * applies to the block model, so the overlay lines up pixel for pixel with the face art.
     * Vanilla applies blockstate (x, y) rotations as {@code rotateYXZ(-y, -x, 0)} about the
     * block's centre.
     */
    public static Quaternionf rotationFor(Direction facing) {
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
        return new Quaternionf().rotateYXZ(-y * Mth.DEG_TO_RAD, -x * Mth.DEG_TO_RAD, 0f);
    }

    private static ResourceLocation texture(String block) {
        return ZPSMod.resource("textures/block/" + block + "_overlay.png");
    }

    private static Model overlay(String block, ResourceLocation texture) {
        // Unlit and unshaded: the overlay is a lamp, its brightness comes from the tint alone.
        Material material = SimpleMaterial.builderOf(Materials.CUTOUT_UNSHADED_BLOCK)
                .texture(texture)
                .polygonOffset(true)
                .useLight(false)
                .ambientOcclusion(false)
                .mipmap(false)
                .build();
        return new SingleMeshModel(northFace(block), material);
    }

    /**
     * One quad on the z = 0 face, wound counter-clockwise as seen from the north, with the same
     * texture mapping vanilla gives a north face: u runs east to west, v top to bottom.
     */
    private static Mesh northFace(String name) {
        MemoryBlock block = MemoryBlock.malloc(4 * PosTexNormalVertexView.STRIDE);
        PosTexNormalVertexView view = new PosTexNormalVertexView();
        view.load(block);

        vertex(view, 0, 1f, 1f, 0f, 0f);
        vertex(view, 1, 1f, 0f, 0f, 1f);
        vertex(view, 2, 0f, 0f, 1f, 1f);
        vertex(view, 3, 0f, 1f, 1f, 0f);

        return new SimpleQuadMesh(view, "zps:" + name + "_overlay");
    }

    private static void vertex(PosTexNormalVertexView view, int index, float x, float y, float u, float v) {
        view.x(index, x);
        view.y(index, y);
        view.z(index, 0f);
        view.u(index, u);
        view.v(index, v);
        view.normalX(index, 0f);
        view.normalY(index, 0f);
        view.normalZ(index, -1f);
    }
}
