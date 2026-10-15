package g_mungus.zps.client.renderer;

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
import g_mungus.zps.block.cableNetwork.CableBlock;
import g_mungus.zps.block.cableNetwork.properties.InsulationType;
import g_mungus.zps.blockentity.RedstoneBandSource;
import g_mungus.zps.client.reactor.ReactorWallOverlays;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

/**
 * The redstone indicator on the Serial Bus and Redstone Converter: a 2px band around the sides of
 * their shared base plate, plus the converter's front window, textured like the converter's and
 * tinted by the block's redstone level, as the Reactor Port's overlay is.
 *
 * <p>The quads are laid out for a block facing down, where the model puts the plate (y 0 to 4,
 * x and z 1 to 15); the visual and renderer turn them with the blockstate's rotation. Each quad
 * sits a sliver proud of its face so it wins the depth test without relying on polygon offset.
 */
public final class RedstoneBand {

    public static final ResourceLocation TEXTURE = ZPSMod.resource("textures/block/redstone_band.png");

    /** How far proud of the plate's sides the band sits. */
    private static final float LIFT = 1f / 512f;
    private static final float MIN = 1f / 16f;
    private static final float MAX = 15f / 16f;
    private static final float BOTTOM = 1f / 16f;
    private static final float TOP = 3f / 16f;

    /** The band's texels: the 14 x 2 at the texture's top left. */
    private static final float U = 14f / 16f;
    private static final float V = 2f / 16f;
    /** The strips run LIFT past the plate's corners too, so neighbouring sides meet. */
    private static final float LO = MIN - LIFT;
    private static final float HI = MAX + LIFT;

    /**
     * The four sides, each as four (x, y, z, u, v) corners wound counter-clockwise as seen from
     * outside, u running left to right as vanilla maps a side face, then the face normal.
     */
    public static final float[][] QUADS = {
            // north
            {HI, TOP, LO, 0, 0, HI, BOTTOM, LO, 0, V, LO, BOTTOM, LO, U, V, LO, TOP, LO, U, 0, 0, 0, -1},
            // south
            {LO, TOP, HI, 0, 0, LO, BOTTOM, HI, 0, V, HI, BOTTOM, HI, U, V, HI, TOP, HI, U, 0, 0, 0, 1},
            // west
            {LO, TOP, LO, 0, 0, LO, BOTTOM, LO, 0, V, LO, BOTTOM, HI, U, V, LO, TOP, HI, U, 0, -1, 0, 0},
            // east
            {HI, TOP, HI, 0, 0, HI, BOTTOM, HI, 0, V, HI, BOTTOM, LO, U, V, HI, TOP, LO, U, 0, 1, 0, 0},
    };

    /** The window's 6 x 6 texels sit below the band's in the texture. */
    private static final float W_LO = 5f / 16f;
    private static final float W_HI = 11f / 16f;
    private static final float W_U = 6f / 16f;
    private static final float W_V0 = 2f / 16f;
    private static final float W_V1 = 8f / 16f;

    /**
     * The Redstone Converter's front window: the 6 x 6 red square in the middle of its facing face,
     * mapped as vanilla maps a down face, so the texels land where the model's own do.
     */
    public static final float[] WINDOW = {
            W_LO, -LIFT, W_HI, 0, W_V0, W_LO, -LIFT, W_LO, 0, W_V1, W_HI, -LIFT, W_LO, W_U, W_V1,
            W_HI, -LIFT, W_HI, W_U, W_V0, 0, -1, 0};

    /** The band, then the converter's window. */
    public static final float[][] QUADS_WITH_WINDOW = {QUADS[0], QUADS[1], QUADS[2], QUADS[3], WINDOW};

    /**
     * Tint at zero output. The texture is the Redstone Converter's band scaled up to its brightest
     * red, so this tint gives back the converter's own colours.
     */
    public static final int OFF_COLOR = 0x580101;

    public static final Model MODEL = model(QUADS, "zps:redstone_band");
    public static final Model MODEL_WITH_WINDOW = model(QUADS_WITH_WINDOW, "zps:redstone_band_with_window");

    private RedstoneBand() {
    }

    /** The band's tint for a redstone level, as packed RGB: the converter's dark red up to the port's bright red. */
    public static int tintRgb(int level) {
        float t = level / 15f;
        return lerpChannel(t, 16) << 16 | lerpChannel(t, 8) << 8 | lerpChannel(t, 0);
    }

    private static int lerpChannel(float t, int shift) {
        return Mth.lerpInt(t, (OFF_COLOR >> shift) & 0xFF, (ReactorWallOverlays.ON_COLOR >> shift) & 0xFF);
    }

    public static float[][] quadsFor(RedstoneBandSource source) {
        return source.hasFrontWindow() ? QUADS_WITH_WINDOW : QUADS;
    }

    public static Model modelFor(RedstoneBandSource source) {
        return source.hasFrontWindow() ? MODEL_WITH_WINDOW : MODEL;
    }

    /** Insulation replaces the plate with its own frame; any other cladding leaves it on show. */
    public static boolean isVisible(BlockState state) {
        return state.getValue(CableBlock.INSULATION_TYPE) != InsulationType.INSULATION;
    }

    /**
     * The blockstates' rotation for a facing, applied the way vanilla applies blockstate
     * (x, y) rotations: {@code rotateYXZ(-y, -x, 0)} about the block's centre.
     */
    public static Quaternionf rotationFor(Direction facing) {
        float x = switch (facing) {
            case DOWN -> 0f;
            case UP -> 180f;
            default -> 270f;
        };
        float y = switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
        return new Quaternionf().rotateYXZ(-y * Mth.DEG_TO_RAD, -x * Mth.DEG_TO_RAD, 0f);
    }

    private static Model model(float[][] quads, String name) {
        // Lit and shaded like the plate it is painted on: the tint says how strong the signal is,
        // the world says how bright it is where the block stands.
        Material material = SimpleMaterial.builderOf(Materials.CUTOUT_BLOCK)
                .texture(TEXTURE)
                .ambientOcclusion(false)
                .mipmap(false)
                .build();
        return new SingleMeshModel(mesh(quads, name), material);
    }

    private static Mesh mesh(float[][] quads, String name) {
        MemoryBlock block = MemoryBlock.malloc(quads.length * 4L * PosTexNormalVertexView.STRIDE);
        PosTexNormalVertexView view = new PosTexNormalVertexView();
        view.load(block);
        int index = 0;
        for (float[] quad : quads) {
            for (int corner = 0; corner < 4; corner++, index++) {
                int at = corner * 5;
                view.x(index, quad[at]);
                view.y(index, quad[at + 1]);
                view.z(index, quad[at + 2]);
                view.u(index, quad[at + 3]);
                view.v(index, quad[at + 4]);
                view.normalX(index, quad[20]);
                view.normalY(index, quad[21]);
                view.normalZ(index, quad[22]);
            }
        }
        return new SimpleQuadMesh(view, name);
    }
}
