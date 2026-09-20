package g_mungus.zps.client.reactor;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

/**
 * The coat over a cavity's wall faces as one static vertex buffer, for {@link ReactorGlowRenderer}
 * to draw: a unit quad on every wall-facing side of every cell, and for a side whose wall block
 * reaches into the cavity, the {@link WallCoats} faces instead.
 *
 * <p>The quads lie on the wall planes themselves, not inset from them: that way two faces meeting
 * at any edge, concave or convex, share exactly that edge with no overlap and no gap. The
 * renderer's polygon offset keeps them in front of the opaque wall in the depth test.
 *
 * <p>Vertices are in the reactor's own frame, measured from the cavity's lowest corner, which is the
 * frame the fragment shader marches in. Each carries its face's outward normal, and in the red
 * channel how deep the cavity runs behind that face in blocks.
 *
 * <p>Needs nothing of a live reactor: a shape is enough, and the walls are optional. That is what
 * lets a preview or a ponder scene draw the glow for a cavity that exists nowhere.
 *
 * <p>Render thread only, and the owner must {@link #close()} it.
 */
public final class ReactorGlowMesh implements AutoCloseable {

    /** Corner offsets of the unit quad on each side of a cell, indexed by {@link Direction} ordinal. */
    private static final float[][] UNIT_FACES = {
            {0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0}, // down
            {0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1}, // up
            {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}, // north
            {0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1}, // south
            {0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1}, // west
            {1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0}, // east
    };

    private final VertexBuffer buffer;
    private final Vector3f size;

    private ReactorGlowMesh(VertexBuffer buffer, Vector3f size) {
        this.buffer = buffer;
        this.size = size;
    }

    /**
     * Builds the coat for a cavity.
     *
     * @param walls where to read the wall blocks from, for the ones that reach into the cavity; null
     *              to take every wall as flat, which is all a cavity that is not in a level can be
     * @return null for a shape with no wall faces, which no real cavity is
     */
    public static @Nullable ReactorGlowMesh build(VoxelShape shape, @Nullable BlockGetter walls) {
        DiscreteVoxelShape grid = CavityShapes.grid(shape);
        BlockPos origin = CavityShapes.origin(shape);
        Long2IntMap masks = ClientReactor.faceMasks(grid, origin);
        if (masks.isEmpty()) {
            return null;
        }

        // Six quads a cell at the very most, wall coats aside; the builder grows past that if it must.
        int capacity = Math.max(1, masks.size()) * 6 * 4 * DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize();
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(capacity)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            BlockPos.MutableBlockPos wall = new BlockPos.MutableBlockPos();
            for (Long2IntMap.Entry entry : masks.long2IntEntrySet()) {
                long cell = entry.getLongKey();
                int x = BlockPos.getX(cell), y = BlockPos.getY(cell), z = BlockPos.getZ(cell);
                long depths = ClientReactor.faceDepths(grid, origin, x, y, z);
                float cx = x - origin.getX(), cy = y - origin.getY(), cz = z - origin.getZ();

                for (Direction side : Direction.values()) {
                    if ((entry.getIntValue() & (1 << side.ordinal())) == 0) {
                        continue;
                    }
                    List<float[]> coat = null;
                    if (walls != null) {
                        wall.set(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
                        coat = WallCoats.facesOf(walls.getBlockState(wall), side.getOpposite());
                    }
                    if (coat == null) {
                        quad(builder, UNIT_FACES[side.ordinal()], cx, cy, cz,
                                side.getStepX(), side.getStepY(), side.getStepZ(), depth(depths, side));
                        continue;
                    }
                    // A coat's faces point every which way, and each takes the depth of the cavity
                    // behind the way it points.
                    for (float[] face : coat) {
                        float nx = face[12], ny = face[13], nz = face[14];
                        quad(builder, face, cx, cy, cz, nx, ny, nz, depth(depths, Direction.getNearest(nx, ny, nz)));
                    }
                }
            }

            MeshData data = builder.build();
            if (data == null) {
                return null;
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(data);
            VertexBuffer.unbind();

            AABB bounds = shape.bounds();
            return new ReactorGlowMesh(buffer,
                    new Vector3f((float) bounds.getXsize(), (float) bounds.getYsize(), (float) bounds.getZsize()));
        }
    }

    private static int depth(long depths, Direction direction) {
        return (int) (depths >>> (8 * direction.ordinal())) & 0xFF;
    }

    private static void quad(BufferBuilder builder, float[] corners, float x, float y, float z,
                             float nx, float ny, float nz, int depth) {
        for (int corner = 0; corner < 4; corner++) {
            builder.addVertex(corners[corner * 3] + x, corners[corner * 3 + 1] + y, corners[corner * 3 + 2] + z)
                    .setColor(depth, 0, 0, 255)
                    .setNormal(nx, ny, nz);
        }
    }

    /** The cavity's bounding box, which in the mesh's frame starts at the origin. */
    public Vector3fc size() {
        return size;
    }

    VertexBuffer buffer() {
        return buffer;
    }

    @Override
    public void close() {
        buffer.close();
    }
}
