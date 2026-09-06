package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The coat for one side of a cavity cell whose wall block reaches into it.
 *
 * <p>{@link ReactorFlywheel#MODEL} coats a cell's sides on the block boundary, which is where the
 * cavity's surface is for the flat walls that make up nearly all of one. A wall block whose model
 * reaches past its own cube — the fuel injector's nozzle does, two pixels of it — moves part of that
 * surface out into the cell, and no single flat quad follows it: left on the boundary the coat has
 * the nozzle standing in front of it, and pulled back to clear the nozzle it leaves the flat part of
 * the face bare.
 *
 * <p>So a side like that is handed over to a coat built for it. The protruding geometry is coated by
 * the quads of the wall's own model that reach past its cube, cut back to the part that does, and
 * the flat part of the face by a ring of quads around the footprint those leave on the boundary.
 * {@link ReactorEffectVisual} clears the side's bit on the cell so the shared mesh leaves it alone,
 * and the two kinds of quad meet along the footprint's edges — the same edges, off the same numbers
 * — so every point of the surface is covered exactly once.
 *
 * <p>Exactly once is the whole point. Leaning on the depth test to hide one coat under another
 * leaves the join doubled, and the material adds rather than replaces, so that reads as a bright
 * seam; backing one coat off to avoid it leaves a hairline the opaque block still covers, which
 * reads as a dark one.
 *
 * <p>Normals are flipped on the protruding quads: a model faces out of its block, and a coat faces
 * out of the cavity, which on a surface the cavity presses against is the other way. That is what
 * tells the fragment shader this is a far face, the same as for the boundary coat.
 *
 * <p>The ring is cut for one footprint, the bounds of everything protruding on that side. A wall
 * with two separate protrusions would be coated across the gap between them rather than around each;
 * nothing has that shape yet, and the fix is to cut the ring per protrusion.
 *
 * <p>One model per wall state and side that needs one, so a reactor built of these adds an instancer
 * per distinct protruding wall rather than per reactor.
 */
public final class WallCoats {

    /** Quads are the same every frame here, so the seed only has to be stable. */
    private static final long SEED = 42L;
    /** How far past its cube a quad has to reach to be worth coating, in blocks. */
    private static final float EPSILON = 1e-4f;
    /** A face is four corners of x, y, z, then the three of its normal. */
    private static final int CORNERS = 4;
    private static final int FACE_FLOATS = CORNERS * 3 + 3;

    /** Per wall state, the coat for each side it protrudes on, indexed by {@link Direction} ordinal. */
    private static final Map<BlockState, Model[]> CACHE = new ConcurrentHashMap<>();

    private WallCoats() {
    }

    /** Clears the cache; call on resource reload, when the models it is built from are re-baked. */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * The whole coat for the side of a cavity cell whose wall is {@code state}, where {@code into}
     * points from that wall into the cell. Null when the wall stops at its own cube on that side —
     * which is the case for nearly every wall block — leaving the cell's own coat to it as usual.
     *
     * <p>Positioned like {@link ReactorFlywheel#MODEL}: relative to the cell's minimum corner.
     */
    public static @Nullable Model of(BlockState state, Direction into) {
        return CACHE.computeIfAbsent(state, WallCoats::build)[into.ordinal()];
    }

    private static Model[] build(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(state);
        RandomSource rand = RandomSource.create(SEED);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, rand, ModelData.EMPTY, null));
        for (Direction cull : Direction.values()) {
            quads.addAll(model.getQuads(state, cull, rand, ModelData.EMPTY, null));
        }

        Model[] coats = new Model[Direction.values().length];
        for (Direction into : Direction.values()) {
            Mesh mesh = coat(quads, into);
            if (mesh != null) {
                coats[into.ordinal()] = new SingleMeshModel(mesh, ReactorFlywheel.MATERIAL);
            }
        }
        return coats;
    }

    /** The coat for one side, or null if the model stops at its own cube there. */
    private static @Nullable Mesh coat(List<BakedQuad> quads, Direction into) {
        // The boundary plane and the two axes across it. Everything stays in the wall block's own
        // frame until the mesh is written, so the plane is at 0 or 1 like any model coordinate.
        boolean positive = into.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int normalAxis = into.getAxis().ordinal();
        int axisA = (normalAxis + 1) % 3;
        int axisB = (normalAxis + 2) % 3;
        float plane = positive ? 1f : 0f;

        List<float[]> faces = new ArrayList<>();
        float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE;
        float minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
        for (BakedQuad quad : quads) {
            if (!reachesPast(quad, positive, normalAxis, plane)) {
                continue;
            }
            float[] face = clipped(quad, positive, normalAxis, plane);
            faces.add(face);
            for (int corner = 0; corner < CORNERS; corner++) {
                minA = Math.min(minA, face[corner * 3 + axisA]);
                maxA = Math.max(maxA, face[corner * 3 + axisA]);
                minB = Math.min(minB, face[corner * 3 + axisB]);
                maxB = Math.max(maxB, face[corner * 3 + axisB]);
            }
        }
        if (faces.isEmpty()) {
            return null;
        }

        // The ring, in the four pieces the footprint leaves the face in. Its inner edges are the
        // footprint's, which is where the cut-back quads stand, so the two meet along one line.
        Direction outward = into.getOpposite();
        float a0 = Math.max(0f, minA), a1 = Math.min(1f, maxA);
        float b0 = Math.max(0f, minB), b1 = Math.min(1f, maxB);
        addRing(faces, normalAxis, axisA, axisB, plane, outward, 0f, 1f, 0f, b0);
        addRing(faces, normalAxis, axisA, axisB, plane, outward, 0f, 1f, b1, 1f);
        addRing(faces, normalAxis, axisA, axisB, plane, outward, 0f, a0, b0, b1);
        addRing(faces, normalAxis, axisA, axisB, plane, outward, a1, 1f, b0, b1);

        return mesh(faces, into);
    }

    /** Whether any corner of the quad clears the block's own cube on the side being coated. */
    private static boolean reachesPast(BakedQuad quad, boolean positive, int normalAxis, float plane) {
        int[] vertices = quad.getVertices();
        for (int corner = 0; corner * IQuadTransformer.STRIDE < vertices.length; corner++) {
            float value = Float.intBitsToFloat(
                    vertices[corner * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + normalAxis]);
            if (positive ? value > plane + EPSILON : value < plane - EPSILON) {
                return true;
            }
        }
        return false;
    }

    /**
     * The quad's corners, cut back to the boundary plane where they fall short of it, and its normal
     * turned to face out of the cavity. Corners move along the axis rather than the quad being
     * split: these protrusions are boxes, whose faces are all either parallel to the plane or square
     * to it, and both come through that exactly.
     *
     * <p>The cut is what stands a protrusion's sides on the plane the ring is cut in, and it drops
     * the part of a straddling quad that is buried in the wall's own solid faces.
     */
    private static float[] clipped(BakedQuad quad, boolean positive, int normalAxis, float plane) {
        int[] vertices = quad.getVertices();
        float[] face = new float[FACE_FLOATS];
        for (int corner = 0; corner < CORNERS; corner++) {
            int base = corner * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            for (int axis = 0; axis < 3; axis++) {
                face[corner * 3 + axis] = Float.intBitsToFloat(vertices[base + axis]);
            }
            float value = face[corner * 3 + normalAxis];
            face[corner * 3 + normalAxis] = positive ? Math.max(value, plane) : Math.min(value, plane);
        }
        return withNormal(face, quad.getDirection().getOpposite());
    }

    /** One piece of the ring, on the boundary plane. Pieces with no area are left out. */
    private static void addRing(List<float[]> faces, int normalAxis, int axisA, int axisB, float plane,
                                Direction outward, float a0, float a1, float b0, float b1) {
        if (a1 - a0 < EPSILON || b1 - b0 < EPSILON) {
            return;
        }
        float[] as = {a0, a0, a1, a1};
        float[] bs = {b0, b1, b1, b0};
        float[] face = new float[FACE_FLOATS];
        for (int corner = 0; corner < CORNERS; corner++) {
            face[corner * 3 + normalAxis] = plane;
            face[corner * 3 + axisA] = as[corner];
            face[corner * 3 + axisB] = bs[corner];
        }
        faces.add(withNormal(face, outward));
    }

    private static float[] withNormal(float[] face, Direction outward) {
        face[CORNERS * 3] = outward.getStepX();
        face[CORNERS * 3 + 1] = outward.getStepY();
        face[CORNERS * 3 + 2] = outward.getStepZ();
        return face;
    }

    /** The faces as a mesh, moved out of the wall block's frame into the cell's. */
    private static Mesh mesh(List<float[]> faces, Direction into) {
        MemoryBlock block = MemoryBlock.malloc((long) faces.size() * CORNERS * PosTexNormalVertexView.STRIDE);
        PosTexNormalVertexView view = new PosTexNormalVertexView();
        view.load(block);

        // The wall block sits one step back from the cell whose corner everything is measured from.
        float shiftX = -into.getStepX();
        float shiftY = -into.getStepY();
        float shiftZ = -into.getStepZ();
        float[][] uvs = {{0f, 0f}, {0f, 1f}, {1f, 1f}, {1f, 0f}};

        int index = 0;
        for (float[] face : faces) {
            for (int corner = 0; corner < CORNERS; corner++) {
                view.x(index, face[corner * 3] + shiftX);
                view.y(index, face[corner * 3 + 1] + shiftY);
                view.z(index, face[corner * 3 + 2] + shiftZ);
                view.u(index, uvs[corner][0]);
                view.v(index, uvs[corner][1]);
                view.normalX(index, face[CORNERS * 3]);
                view.normalY(index, face[CORNERS * 3 + 1]);
                view.normalZ(index, face[CORNERS * 3 + 2]);
                index++;
            }
        }
        return new SimpleQuadMesh(view, "zps:reactor_wall_coat");
    }
}
