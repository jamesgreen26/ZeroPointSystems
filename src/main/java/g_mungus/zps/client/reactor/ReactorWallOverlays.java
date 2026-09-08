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
import net.minecraft.resources.ResourceLocation;

/**
 * The indicator overlays drawn on the outer face of the Fuel Injector and the Exhaust Port: one
 * full-face quad per block carrying that block's {@code *_overlay} texture, tinted per instance by
 * the redstone level the block receives.
 *
 * <p>The quad lies on the block's north face, where the models put the outer face; the visual turns
 * it to the block's facing. It sits exactly on the face plane and relies on the material's polygon
 * offset to win the depth test against the opaque block behind it. Both models are static
 * singletons: Flywheel keys instancers by model identity, so every injector shares one draw.
 */
public final class ReactorWallOverlays {

    public static final Model FUEL_INJECTOR = overlay("fuel_injector");
    public static final Model EXHAUST_PORT = overlay("exhaust_port");

    private ReactorWallOverlays() {
    }

    private static Model overlay(String block) {
        ResourceLocation texture = ZPSMod.resource("textures/block/" + block + "_overlay.png");
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
