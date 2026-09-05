package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.Layout;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.layout.UnsignedIntegerRepr;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.DepthTest;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.FogShaders;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import g_mungus.zps.ZPSMod;
import org.lwjgl.system.MemoryUtil;

/**
 * The Flywheel side of the reactor glow: the instance layout, the material, and the one shared
 * model. The glow is a coat over every wall face of the cavity, drawn from the inside. All static: the model in particular must be a single object, because Flywheel keys
 * instancers by model identity and a model per reactor would put every reactor in its own draw.
 */
public final class ReactorFlywheel {

    private ReactorFlywheel() {
    }

    private static final Layout LAYOUT = LayoutBuilder.create()
            .vector("pos", FloatRepr.FLOAT, 3)
            .vector("boxMin", FloatRepr.FLOAT, 3)
            .vector("boxMax", FloatRepr.FLOAT, 3)
            .vector("params", FloatRepr.FLOAT, 2)
            .scalar("faces", UnsignedIntegerRepr.UNSIGNED_INT)
            .vector("depths", UnsignedIntegerRepr.UNSIGNED_INT, 2)
            .build();

    // Offsets come from the layout so the writer can never drift from the GLSL struct.
    private static final int POS = LAYOUT.asMap().get("pos").byteOffset();
    private static final int BOX_MIN = LAYOUT.asMap().get("boxMin").byteOffset();
    private static final int BOX_MAX = LAYOUT.asMap().get("boxMax").byteOffset();
    private static final int PARAMS = LAYOUT.asMap().get("params").byteOffset();
    private static final int FACES = LAYOUT.asMap().get("faces").byteOffset();
    private static final int DEPTHS = LAYOUT.asMap().get("depths").byteOffset();

    public static final InstanceType<ReactorCellInstance> INSTANCE_TYPE = SimpleInstanceType.builder(ReactorCellInstance::new)
            .layout(LAYOUT)
            .writer((ptr, instance) -> {
                MemoryUtil.memPutFloat(ptr + POS, instance.x);
                MemoryUtil.memPutFloat(ptr + POS + 4, instance.y);
                MemoryUtil.memPutFloat(ptr + POS + 8, instance.z);
                MemoryUtil.memPutFloat(ptr + BOX_MIN, instance.minX);
                MemoryUtil.memPutFloat(ptr + BOX_MIN + 4, instance.minY);
                MemoryUtil.memPutFloat(ptr + BOX_MIN + 8, instance.minZ);
                MemoryUtil.memPutFloat(ptr + BOX_MAX, instance.maxX);
                MemoryUtil.memPutFloat(ptr + BOX_MAX + 4, instance.maxY);
                MemoryUtil.memPutFloat(ptr + BOX_MAX + 8, instance.maxZ);
                MemoryUtil.memPutFloat(ptr + PARAMS, instance.intensity);
                MemoryUtil.memPutFloat(ptr + PARAMS + 4, instance.seed);
                MemoryUtil.memPutInt(ptr + FACES, instance.faces);
                MemoryUtil.memPutInt(ptr + DEPTHS, instance.depthsLow);
                MemoryUtil.memPutInt(ptr + DEPTHS + 4, instance.depthsHigh);
            })
            .vertexShader(ZPSMod.resource("instance/reactor_cell.vert"))
            .cullShader(ZPSMod.resource("instance/cull/reactor_cell.glsl"))
            .build();

    /**
     * Texture-less: the fragment shader writes the colour outright. A plain white texture is
     * bound so every backend has a valid sampler and nothing tints the result.
     */
    public static final Material MATERIAL = SimpleMaterial.builder()
            .shaders(new SimpleMaterialShaders(
                    ZPSMod.resource("material/reactor_volume.vert"),
                    ZPSMod.resource("material/reactor_volume.frag")))
            .texture(ZPSMod.resource("textures/special/white.png"))
            .fog(FogShaders.LINEAR)
            .cutout(CutoutShaders.OFF)
            .light(LightShaders.FLAT)
            .transparency(Transparency.LIGHTNING)
            .writeMask(WriteMask.COLOR)
            .depthTest(DepthTest.LEQUAL)
            // The coat lies on the wall planes; this pulls it in front of the opaque wall's depth.
            .polygonOffset(true)
            .backfaceCulling(false)
            .useOverlay(false)
            .useLight(false)
            .cardinalLightingMode(CardinalLightingMode.OFF)
            .ambientOcclusion(false)
            .mipmap(false)
            .blur(false)
            .build();

    /**
     * Six unit faces exactly on the cell's six sides. Each carries the outward normal of the
     * side it lines, which is how the vertex shader tells which face it is drawing; faces not
     * against a wall are collapsed there.
     *
     * <p>On the wall plane itself, not inset from it: that way two faces meeting at any edge,
     * concave or convex, share exactly that edge with no overlap and no gap. The material's
     * polygon offset keeps them in front of the opaque wall in the depth test.
     */
    public static final Model MODEL = new SingleMeshModel(coatMesh(), MATERIAL);

    private static Mesh coatMesh() {
        MemoryBlock block = MemoryBlock.malloc(24 * PosTexNormalVertexView.STRIDE);
        PosTexNormalVertexView view = new PosTexNormalVertexView();
        view.load(block);

        float lo = 0f;
        float hi = 1f;
        int v = 0;
        // Down (y = 0 side) and up (y = 1 side).
        v = quad(view, v, 0f, lo, 0f, 0f, lo, 1f, 1f, lo, 1f, 1f, lo, 0f, 0f, -1f, 0f);
        v = quad(view, v, 0f, hi, 0f, 1f, hi, 0f, 1f, hi, 1f, 0f, hi, 1f, 0f, 1f, 0f);
        // North (z = 0 side) and south (z = 1 side).
        v = quad(view, v, 0f, 0f, lo, 1f, 0f, lo, 1f, 1f, lo, 0f, 1f, lo, 0f, 0f, -1f);
        v = quad(view, v, 0f, 0f, hi, 0f, 1f, hi, 1f, 1f, hi, 1f, 0f, hi, 0f, 0f, 1f);
        // West (x = 0 side) and east (x = 1 side).
        v = quad(view, v, lo, 0f, 0f, lo, 1f, 0f, lo, 1f, 1f, lo, 0f, 1f, -1f, 0f, 0f);
        quad(view, v, hi, 0f, 0f, hi, 0f, 1f, hi, 1f, 1f, hi, 1f, 0f, 1f, 0f, 0f);

        return new SimpleQuadMesh(view, "zps:reactor_cell");
    }

    /** Four corners in order, then the normal. Texture coordinates run 0..1 across the quad. */
    private static int quad(PosTexNormalVertexView view, int start,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float x2, float y2, float z2, float x3, float y3, float z3,
                            float nx, float ny, float nz) {
        float[][] corners = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        float[][] uvs = {{0f, 0f}, {0f, 1f}, {1f, 1f}, {1f, 0f}};
        for (int i = 0; i < 4; i++) {
            int index = start + i;
            view.x(index, corners[i][0]);
            view.y(index, corners[i][1]);
            view.z(index, corners[i][2]);
            view.u(index, uvs[i][0]);
            view.v(index, uvs[i][1]);
            view.normalX(index, nx);
            view.normalY(index, ny);
            view.normalZ(index, nz);
        }
        return start + 4;
    }
}
