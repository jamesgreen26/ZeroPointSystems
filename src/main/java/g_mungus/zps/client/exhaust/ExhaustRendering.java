package g_mungus.zps.client.exhaust;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.IntegerRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleLightShader;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import g_mungus.zps.ZPSMod;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

/**
 * The Flywheel side of exhaust particles: the instance type that reads the batch ring, the
 * material, and the cube every particle is drawn with.
 *
 * <p>Every particle is drawn once, writing both targets of the weighted blended transparency
 * {@link ExhaustLayer} composites. The layer recognises the material by identity and sends its
 * draws to its own targets with its own blending; the transparency and write mask here only have
 * to keep Flywheel from writing depth.
 *
 * <p>Under a shader pack, through Colorwheel, the pack draws Flywheel geometry into its own targets
 * and the layer cannot take the draws. Each particle is then drawn with {@link #PACK_MATERIAL}
 * instead: ordinary unsorted translucency that the pack lights and composites as it does any other.
 *
 * <p>The materials' fragment shaders read the heat the instance shader puts in the texture
 * coordinate, so the two only work as a pair; nothing else should draw with these materials.
 */
public final class ExhaustRendering {

    public static final InstanceType<ExhaustInstance> INSTANCE_TYPE = SimpleInstanceType.builder(ExhaustInstance::new)
            .layout(LayoutBuilder.create()
                    .scalar("slot", IntegerRepr.INT)
                    .build())
            .writer((ptr, instance) -> MemoryUtil.memPutInt(ptr, instance.slot))
            .vertexShader(ZPSMod.resource("exhaust/exhaust.vert"))
            .cullShader(ZPSMod.resource("exhaust/cull.glsl"))
            .build();

    /** Plain white, so sampling it at the heat the texture coordinate carries changes nothing. */
    private static final ResourceLocation WHITE = ZPSMod.resource("textures/effect/exhaust_white.png");

    /** Weighted colour, lit, fogged and summed, and alongside it transmittance and nearest depth. */
    public static final Material MATERIAL = SimpleMaterial.builder()
            .shaders(new SimpleMaterialShaders(
                    ZPSMod.resource("exhaust/material.vert"),
                    ZPSMod.resource("exhaust/exhaust.frag")))
            .texture(WHITE)
            .mipmap(false)
            .transparency(Transparency.TRANSLUCENT)
            .writeMask(WriteMask.COLOR)
            // Each particle is lit once, in the instance shader, from the sections the effect tracks.
            .light(new SimpleLightShader(ZPSMod.resource("exhaust/light.glsl")))
            // The fragment shader shades smoke by face itself and leaves fire unshaded.
            .cardinalLightingMode(CardinalLightingMode.OFF)
            .useOverlay(false)
            .build();

    /** Under a shader pack: colour over what is behind, lit and fogged by the pack. */
    public static final Material PACK_MATERIAL = SimpleMaterial.builderOf(MATERIAL)
            .shaders(new SimpleMaterialShaders(
                    ZPSMod.resource("exhaust/material.vert"),
                    ZPSMod.resource("exhaust/pack.frag")))
            .build();

    private static final CubeMesh MESH = new CubeMesh();
    public static final Model CUBE = new SingleMeshModel(MESH, MATERIAL);
    public static final Model PACK_CUBE = new SingleMeshModel(MESH, PACK_MATERIAL);

    /** Colorwheel's backends, which Flywheel only picks while an Iris shader pack is in use. */
    private static final String COLORWHEEL = "colorwheel";

    private ExhaustRendering() {
    }

    /** Whether Flywheel draws through a shader pack, so exhaust must be drawn with {@link #PACK_MATERIAL}. */
    public static boolean throughShaderPack() {
        Backend backend = BackendManager.currentBackend();
        ResourceLocation id = Backend.REGISTRY.getId(backend);
        return id != null && COLORWHEEL.equals(id.getNamespace());
    }

    /** A unit cube centred on the origin, so the shader can scale it about the particle's centre. */
    private static final class CubeMesh implements QuadMesh {

        // Per face: normal, then two edge directions whose cross product is the normal, so each
        // quad winds anticlockwise seen from outside.
        private static final int[][] FACES = {
                {1, 0, 0, 0, 1, 0, 0, 0, 1},
                {-1, 0, 0, 0, 0, 1, 0, 1, 0},
                {0, 1, 0, 0, 0, 1, 1, 0, 0},
                {0, -1, 0, 1, 0, 0, 0, 0, 1},
                {0, 0, 1, 1, 0, 0, 0, 1, 0},
                {0, 0, -1, 0, 1, 0, 1, 0, 0},
        };
        private static final int[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        private static final Vector4fc BOUNDS = new Vector4f(0, 0, 0, (float) Math.sqrt(0.75));

        @Override
        public int vertexCount() {
            return FACES.length * 4;
        }

        @Override
        public void write(MutableVertexList vertices) {
            int i = 0;
            for (int[] f : FACES) {
                for (int[] c : CORNERS) {
                    vertices.x(i, 0.5f * (f[0] + c[0] * f[3] + c[1] * f[6]));
                    vertices.y(i, 0.5f * (f[1] + c[0] * f[4] + c[1] * f[7]));
                    vertices.z(i, 0.5f * (f[2] + c[0] * f[5] + c[1] * f[8]));
                    vertices.r(i, 1);
                    vertices.g(i, 1);
                    vertices.b(i, 1);
                    vertices.a(i, 1);
                    vertices.u(i, 0);
                    vertices.v(i, 0);
                    vertices.overlay(i, OverlayTexture.NO_OVERLAY);
                    vertices.light(i, 0);
                    vertices.normalX(i, f[0]);
                    vertices.normalY(i, f[1]);
                    vertices.normalZ(i, f[2]);
                    i++;
                }
            }
        }

        @Override
        public Vector4fc boundingSphere() {
            return BOUNDS;
        }
    }
}
