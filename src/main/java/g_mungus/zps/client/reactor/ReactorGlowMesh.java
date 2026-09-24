package g_mungus.zps.client.reactor;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelDataManager;
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
 * <p>With it, the windows: the inner face of every translucent wall block, as the block's own model
 * draws it, connected textures and lighting and all. The glow is drawn ahead of the level's
 * translucent pass so that a window between the eye and the plasma goes over the plasma, and left at
 * that a window on the far side would go over it too, tint and frame painted across plasma that is
 * in front of them. So the renderer draws these first, writing depth: the plasma lands on top of
 * them, and when the level comes to draw the same faces they fail the depth test against ours.
 * Culled by winding, so only a window seen from its cavity side is ever taken over.
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
    /** The windows' inner faces, in the block vertex format; null for a cavity without any. */
    private final @Nullable VertexBuffer windows;
    private final Vector3f size;
    /** Every wall block a face was cut against, and for the windows the cell that lights each. */
    private final long[] wallPositions;
    private final long[] windowCells;
    private final int signature;

    private ReactorGlowMesh(VertexBuffer buffer, @Nullable VertexBuffer windows, Vector3f size,
                            long[] wallPositions, long[] windowCells, int signature) {
        this.buffer = buffer;
        this.windows = windows;
        this.size = size;
        this.wallPositions = wallPositions;
        this.windowCells = windowCells;
        this.signature = signature;
    }

    /**
     * Builds the coat for a cavity.
     *
     * @param walls where to read the wall blocks from, for the ones that reach into the cavity; null
     *              to take every wall as flat, which is all a cavity that is not in a level can be
     * @return null for a shape with no wall faces, which no real cavity is
     */
    public static @Nullable ReactorGlowMesh build(VoxelShape shape, @Nullable BlockAndTintGetter walls) {
        return build(shape, walls, true);
    }

    /**
     * As {@link #build(VoxelShape, BlockAndTintGetter)}, with the windows optional. They exist to
     * beat the level's translucent pass to the far glass, and are drawn in the level's own shader;
     * somewhere that is not the level render, a ponder scene say, has neither and leaves them out.
     */
    public static @Nullable ReactorGlowMesh build(VoxelShape shape, @Nullable BlockAndTintGetter walls,
                                                  boolean withWindows) {
        DiscreteVoxelShape grid = CavityShapes.grid(shape);
        BlockPos origin = CavityShapes.origin(shape);
        Long2IntMap masks = ClientReactor.faceMasks(grid, origin);
        if (masks.isEmpty()) {
            return null;
        }

        // Six quads a cell at the very most, wall coats aside; the builder grows past that if it must.
        int capacity = Math.max(1, masks.size()) * 6 * 4 * DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize();
        LongList wallPositions = new LongArrayList();
        LongList windowCells = new LongArrayList();
        {
            BufferBuilder builder = new BufferBuilder(capacity);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            BufferBuilder windowBuilder = new BufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 4 * 16);
            windowBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            PoseStack windowPose = new PoseStack();
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
                        BlockState state = walls.getBlockState(wall);
                        coat = WallCoats.facesOf(state, side.getOpposite());
                        wallPositions.add(wall.asLong());
                        if (withWindows
                                && window(windowBuilder, windowPose, walls, state, wall, origin, side.getOpposite())) {
                            windowCells.add(cell);
                        }
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

            BufferBuilder.RenderedBuffer data = builder.end();
            BufferBuilder.RenderedBuffer windowData = windowBuilder.end();
            if (data.isEmpty()) {
                data.release();
                windowData.release();
                return null;
            }
            VertexBuffer buffer = upload(data);
            VertexBuffer windows;
            if (windowData.isEmpty()) {
                windowData.release();
                windows = null;
            } else {
                windows = upload(windowData);
            }

            AABB bounds = shape.bounds();
            long[] wallArray = wallPositions.toLongArray();
            long[] windowCellArray = windowCells.toLongArray();
            return new ReactorGlowMesh(buffer, windows,
                    new Vector3f((float) bounds.getXsize(), (float) bounds.getYsize(), (float) bounds.getZsize()),
                    wallArray, windowCellArray,
                    walls == null ? 0 : signature(walls, wallArray, windowCellArray));
        }
    }

    private static VertexBuffer upload(BufferBuilder.RenderedBuffer data) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(data);
        VertexBuffer.unbind();
        return buffer;
    }

    /**
     * Adds a wall block's inner face to the windows if the block draws in the translucent layer, and
     * says whether it did.
     *
     * <p>Through the vanilla block tesselator rather than straight from the quads, so that the face
     * comes out shaded, lit and tinted exactly as the chunk has it: the two swap places the moment
     * the reactor's glow becomes visible, and any difference would show as a flicker of the glass.
     * The model data is asked of the model the way the chunk asks, which is where a connected
     * texture learns its neighbours.
     *
     * @param into the wall block's face on the cavity
     */
    private static boolean window(BufferBuilder builder, PoseStack pose, BlockAndTintGetter level, BlockState state,
                                  BlockPos pos, BlockPos origin, Direction into) {
        if (state.isAir()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        long seed = state.getSeed(pos);
        RandomSource random = RandomSource.create(seed);
        ModelDataManager modelDataManager = level.getModelDataManager();
        ModelData existing = modelDataManager == null ? null : modelDataManager.getAt(pos);
        ModelData data = model.getModelData(level, pos, state, existing == null ? ModelData.EMPTY : existing);
        if (!model.getRenderTypes(state, random, data).contains(RenderType.translucent())) {
            return false;
        }

        pose.pushPose();
        pose.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
        // Sides unchecked: the one face wanted is against the cavity, and is wanted whatever is in it.
        minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(level, new OneSide(model, into), state, pos,
                pose, builder, false, random, seed, OverlayTexture.NO_OVERLAY, data, RenderType.translucent());
        pose.popPose();
        return true;
    }

    /** A model cut down to the quads culled by one side, which for a full block is that side's face. */
    private static final class OneSide extends BakedModelWrapper<BakedModel> {
        private final Direction side;

        OneSide(BakedModel model, Direction side) {
            super(model);
            this.side = side;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                        ModelData data, @Nullable RenderType renderType) {
            return side == this.side ? originalModel.getQuads(state, side, rand, data, renderType) : List.of();
        }
    }

    /**
     * What the mesh was cut from, boiled down: which block every wall is, and the light on every
     * window. The mesh bakes all of that in, so when this no longer matches, it wants building again.
     */
    private static int signature(BlockAndTintGetter level, long[] wallPositions, long[] windowCells) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int hash = 1;
        for (long wall : wallPositions) {
            hash = 31 * hash + Block.getId(level.getBlockState(cursor.set(wall)));
        }
        for (long cell : windowCells) {
            hash = 31 * hash + LevelRenderer.getLightColor(level, cursor.set(cell));
        }
        return hash;
    }

    /** Whether the walls or the light on the windows have changed since the mesh was built. */
    boolean isStale(BlockAndTintGetter level) {
        return wallPositions.length > 0 && signature(level, wallPositions, windowCells) != signature;
    }

    private static int depth(long depths, Direction direction) {
        return (int) (depths >>> (8 * direction.ordinal())) & 0xFF;
    }

    private static void quad(BufferBuilder builder, float[] corners, float x, float y, float z,
                             float nx, float ny, float nz, int depth) {
        for (int corner = 0; corner < 4; corner++) {
            builder.vertex(corners[corner * 3] + x, corners[corner * 3 + 1] + y, corners[corner * 3 + 2] + z)
                    .color(depth, 0, 0, 255)
                    .normal(nx, ny, nz)
                    .endVertex();
        }
    }

    /** The cavity's bounding box, which in the mesh's frame starts at the origin. */
    public Vector3fc size() {
        return size;
    }

    VertexBuffer buffer() {
        return buffer;
    }

    @Nullable VertexBuffer windows() {
        return windows;
    }

    @Override
    public void close() {
        buffer.close();
        if (windows != null) {
            windows.close();
        }
    }
}
