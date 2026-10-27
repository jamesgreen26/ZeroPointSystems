package g_mungus.zps.client.model.connected;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ConnectingBakedModel extends BakedModelWrapper<BakedModel> {
    /**
     * Identifies one neighbour lookup: the world direction of the face being drawn, and the offset from the
     * block being rendered to the neighbour. Keyed by face as well as offset because a rule may care which
     * face it is drawing — the same neighbour can connect on one face of a block and not on another.
     * With {@code match_surface}, {@code probe} is where the face's edge meets the neighbour; the neighbour only
     * connects if its shape carries the surface on somewhere along it.
     */
    public record Neighbour(Direction face, Vec3i offset, @Nullable Probe probe) {}

    /**
     * A stretch of the face's plane just inside the neighbour, in the neighbour's own block coordinates: the
     * shared edge for a side neighbour, or a single point ({@code from == to}) for a diagonal one.
     */
    public record Probe(Vec3 from, Vec3 to) {}

    /** How far, in blocks, a surface probe sits inside the neighbour and either side of the face's plane. */
    private static final double PROBE_INSET = 1 / 64.0;

    /** Maps each relevant neighbour lookup to whether it connects, for the block being rendered. */
    public static final ModelProperty<Map<Neighbour, Boolean>> CONNECTIONS = new ModelProperty<>();

    private static final int SIDE_COUNT = 7; // 6 directions + 1 for the null (unculled) bucket

    /**
     * Blocks that connection rules see as another block: another mod's reshaped copy of one of ours (a Clutter
     * No More vertical slab or step) stands in for the slab or stairs it was made from. Rebuilt on each bake.
     */
    private static volatile Map<Block, Block> aliases = Map.of();

    private final List<List<BakedQuad>> staticBySide;
    private final List<List<ConnectingFace>> connectingBySide;
    private final List<List<BakedQuad>> itemBySide;
    private final BakedModel itemModel;
    private final List<ConnectionRule> rules;
    private final boolean matchSurface;
    /** Place tiles by where each face sits in the block rather than by its UVs; see {@link #connect}. */
    private final boolean worldUv;
    private final Set<Neighbour> neighbours;
    private final RandomSource rand;
    private final RenderTypeGroup renderTypes;
    /** Looks up an atlas sprite by name, for the per-tile sprites of {@link PiecedTileSource}. */
    private final Function<ResourceLocation, TextureAtlasSprite> sprites;

    public ConnectingBakedModel(BakedModel original, List<ConnectionRule> rules, boolean matchSurface,
                                BlockState owner, RandomSource rand, RenderTypeGroup renderTypes,
                                Function<ResourceLocation, TextureAtlasSprite> sprites) {
        this(original, rules, matchSurface, owner, rand, renderTypes, sprites, false);
    }

    private ConnectingBakedModel(BakedModel original, List<ConnectionRule> rules, boolean matchSurface,
                                 BlockState owner, RandomSource rand, RenderTypeGroup renderTypes,
                                 Function<ResourceLocation, TextureAtlasSprite> sprites, boolean worldUv) {
        super(original);
        this.worldUv = worldUv;
        this.sprites = sprites;
        this.rules = rules;
        this.matchSurface = matchSurface;
        this.rand = rand;
        this.renderTypes = renderTypes;
        this.staticBySide = new ArrayList<>(SIDE_COUNT);
        this.connectingBySide = new ArrayList<>(SIDE_COUNT);
        this.itemBySide = new ArrayList<>(SIDE_COUNT);
        for (int i = 0; i < SIDE_COUNT; i++) {
            staticBySide.add(new ArrayList<>());
            connectingBySide.add(new ArrayList<>());
            itemBySide.add(new ArrayList<>());
        }
        Set<Neighbour> lookups = new HashSet<>();

        sortQuads(original.getQuads(owner, null, rand), SIDE_COUNT - 1, lookups);
        for (Direction side : Direction.values()) {
            sortQuads(original.getQuads(owner, side, rand), side.get3DDataValue(), lookups);
        }

        this.neighbours = lookups;
        this.itemModel = new BakedModelWrapper<>(original) {
            @Override
            public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
                return itemQuads(side);
            }

            @Override
            public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                            ModelData extraData, @Nullable RenderType renderType) {
                return itemQuads(side);
            }

            @Override
            public BakedModel applyTransform(ItemDisplayContext cameraTransformType, PoseStack poseStack,
                                             boolean applyLeftHandTransform) {
                original.applyTransform(cameraTransformType, poseStack, applyLeftHandTransform);
                return this;
            }

            @Override
            public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
                if (renderTypes.isEmpty()) {
                    return original.getRenderTypes(itemStack, fabulous);
                }
                return List.of(fabulous ? renderTypes.entityFabulous() : renderTypes.entity());
            }
        };
    }

    /**
     * Wraps another model so its connecting textures connect by this model's rules. Its render types are its own.
     * Its UVs are ignored: another mod's templates need not map a face to the part of the texture a full block
     * would show there (Clutter No More's steps give the inner face the top half, bottom step or top), so tiles
     * are placed by where each face sits in the block, as on a full block. Null if it has no connecting faces,
     * or is already connecting.
     */
    public @Nullable ConnectingBakedModel connect(BakedModel other) {
        if (other instanceof ConnectingBakedModel) {
            return null;
        }
        ConnectingBakedModel wrapped = new ConnectingBakedModel(other, rules, matchSurface, null, rand,
                RenderTypeGroup.EMPTY, sprites, true);
        return wrapped.connectingBySide.stream().allMatch(List::isEmpty) ? null : wrapped;
    }

    public static void setAliases(Map<Block, Block> aliases) {
        ConnectingBakedModel.aliases = Map.copyOf(aliases);
    }

    private static BlockState alias(BlockState state) {
        Block source = aliases.get(state.getBlock());
        return source == null ? state : source.defaultBlockState();
    }

    private void sortQuads(List<BakedQuad> quads, int sideIndex, Set<Neighbour> lookups) {
        for (BakedQuad quad : quads) {
            ConnectedTextureMeta.Meta meta = ConnectedTextureMeta.get(quad.getSprite().contents().name()).orElse(null);
            int tileCount = quad.getSprite().contents().width() / quad.getSprite().contents().height();
            if (meta != null && meta.pieced() && tileCount >= PiecedLayout.TILE_COUNT) {
                ConnectingFace face = new ConnectingFace(quad, tiles(quad.getSprite()), matchSurface, worldUv);
                connectingBySide.get(sideIndex).add(face);
                face.emit(itemBySide.get(sideIndex), null);
                lookups.addAll(face.neighbours());
            } else {
                staticBySide.get(sideIndex).add(quad);
                itemBySide.get(sideIndex).add(quad);
            }
        }
    }

    private TextureAtlasSprite[] tiles(TextureAtlasSprite strip) {
        TextureAtlasSprite[] tiles = new TextureAtlasSprite[PiecedLayout.TILE_COUNT];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = sprites.apply(PiecedTileSource.tile(strip.contents().name(), i));
        }
        return tiles;
    }

    private List<BakedQuad> itemQuads(@Nullable Direction side) {
        return itemBySide.get(side == null ? SIDE_COUNT - 1 : side.get3DDataValue());
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext cameraTransformType, PoseStack poseStack,
                                     boolean applyLeftHandTransform) {
        originalModel.applyTransform(cameraTransformType, poseStack, applyLeftHandTransform);
        return this;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (neighbours.isEmpty()) {
            return modelData;
        }
        // Rendered as another block's material (a copycat): our full-cube faces get cropped to that block's shape,
        // so surface probes would test the wrong plane. The copycat's level already hides neighbours whose faces
        // don't line up, so its answer stands in for the probe.
        boolean disguised = level.getBlockState(pos) != state;
        BlockState self = alias(state);
        Map<Neighbour, Boolean> connections = new HashMap<>(neighbours.size());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Neighbour neighbour : neighbours) {
            Vec3i offset = neighbour.offset();
            cursor.setWithOffset(pos, offset.getX(), offset.getY(), offset.getZ());
            BlockState other = level.getBlockState(cursor);
            // a copycat neighbour looks like its material
            BlockState appearance = other.getAppearance(level, cursor, neighbour.face(), state, pos);
            boolean connects = ConnectionRule.anyMatch(rules, self, alias(appearance), neighbour.face())
                    && (neighbour.probe() == null || disguised
                        || continuesSurface(other.getShape(level, cursor), neighbour.face(), neighbour.probe()));
            connections.put(neighbour, connects);
        }
        return modelData.derive().with(CONNECTIONS, connections).build();
    }

    /**
     * Whether a shape is solid just behind the probe and open just in front of it — i.e. has a face there — at
     * any point along it. The probe is split wherever a box of the shape starts or ends, and each stretch is
     * tested at its middle, so a test point never lands on a box's boundary. (A single midpoint test did: the
     * side of a wall's arm is centred exactly on the edge of the post at the end of a run.)
     */
    private static boolean continuesSurface(VoxelShape shape, Direction face, Probe probe) {
        List<AABB> boxes = shape.toAabbs();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal()).scale(PROBE_INSET);
        Vec3 span = probe.to().subtract(probe.from());
        List<Double> cuts = new ArrayList<>();
        cuts.add(0.0);
        cuts.add(1.0);
        for (AABB box : boxes) {
            for (Direction.Axis axis : Direction.Axis.values()) {
                double length = span.get(axis);
                if (length != 0) {
                    addCut(cuts, (box.min(axis) - probe.from().get(axis)) / length);
                    addCut(cuts, (box.max(axis) - probe.from().get(axis)) / length);
                }
            }
        }
        cuts.sort(null);
        for (int i = 0; i + 1 < cuts.size(); i++) {
            double a = cuts.get(i);
            double b = cuts.get(i + 1);
            if (b - a < 1e-6) {
                continue;
            }
            Vec3 point = probe.from().add(span.scale((a + b) * 0.5));
            if (contains(boxes, point.subtract(normal)) && !contains(boxes, point.add(normal))) {
                return true;
            }
        }
        return false;
    }

    private static void addCut(List<Double> cuts, double t) {
        if (t > 0 && t < 1) {
            cuts.add(t);
        }
    }

    private static boolean contains(List<AABB> boxes, Vec3 point) {
        for (AABB box : boxes) {
            if (box.contains(point)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return collect(side, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData extraData, @Nullable RenderType renderType) {
        return collect(side, extraData.get(CONNECTIONS));
    }

    private List<BakedQuad> collect(@Nullable Direction side, @Nullable Map<Neighbour, Boolean> connections) {
        int index = side == null ? SIDE_COUNT - 1 : side.get3DDataValue();
        List<ConnectingFace> faces = connectingBySide.get(index);
        List<BakedQuad> staticQuads = staticBySide.get(index);
        List<BakedQuad> out = new ArrayList<>(staticQuads.size() + faces.size() * 4);
        out.addAll(staticQuads);
        for (ConnectingFace face : faces) {
            face.emit(out, connections);
        }
        return out;
    }

    @Override
    public @NotNull List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        return List.of(itemModel);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return renderTypes.isEmpty() ? originalModel.getRenderTypes(state, rand, data) : ChunkRenderTypeSet.of(renderTypes.block());
    }

    /**
     * Pre-computed geometry for one connecting face. The face is mapped onto a 16px tile through its UVs, and
     * split at the tile's centre into up to four pieces, one per tile corner, each clipped to the part of the
     * tile the face actually covers — so a slab's half-height side only draws the two lower corners. Holds the
     * four texture-space corner positions, per-vertex texture-space coordinates (to preserve winding), the
     * world-space neighbour directions for the texture axes, and a template vertex array used to copy
     * colour/light/normal data into the generated sub-quads.
     */
    private static final class ConnectingFace {
        /**
         * How close, in tile pixels, a face must come to the tile's edge to count as reaching it. Generous because
         * baking shrinks UVs a little towards the sprite's centre; model faces sit on whole pixels anyway.
         */
        private static final float EDGE_SLACK = 0.5f;

        private final int[] template; // original 4-vertex data; position+uv are overwritten per sub-quad
        /** One sprite per strip tile; pieces are emitted against these, never the strip. */
        private final TextureAtlasSprite[] tiles;
        private final int tintIndex;
        private final Direction direction;
        private final boolean shade;
        private final boolean hasAmbientOcclusion;
        private final boolean matchSurface;
        // the face's extent on the tile, in tile pixels (0-16)
        private final float sMin;
        private final float sMax;
        private final float tMin;
        private final float tMax;
        // texture-space corner positions
        private final Vector3f topLeft;
        private final Vector3f topRight;
        private final Vector3f bottomLeft;
        private final Vector3f bottomRight;
        // per original vertex: texture-space corner (false=0/left or top, true=1/right or bottom)
        private final boolean[] vertexRight = new boolean[4];
        private final boolean[] vertexBottom = new boolean[4];
        // world offsets of texture +u and +v axes
        private final Vec3i axisU;
        private final Vec3i axisV;
        private final List<Piece> pieces = new ArrayList<>();

        /**
         * One tile corner clipped to the face, with the lookups that choose its tile. A null lookup is an edge
         * the piece does not reach: its tile border is never drawn, so it counts as connected.
         */
        private record Piece(int corner, float sLo, float sHi, float tLo, float tHi,
                             @Nullable Neighbour horizontal, @Nullable Neighbour vertical,
                             @Nullable Neighbour diagonal) {}

        ConnectingFace(BakedQuad quad, TextureAtlasSprite[] tiles, boolean matchSurface, boolean worldUv) {
            this.template = quad.getVertices().clone();
            this.tiles = tiles;
            this.tintIndex = quad.getTintIndex();
            this.direction = quad.getDirection();
            this.shade = quad.isShade();
            this.hasAmbientOcclusion = quad.hasAmbientOcclusion();
            this.matchSurface = matchSurface;
            TextureAtlasSprite strip = quad.getSprite();
            float u0 = strip.getU0();
            float u1 = strip.getU1();
            float v0 = strip.getV0();
            float v1 = strip.getV1();

            Vector3f[] positions = new Vector3f[4];
            float[] ss = new float[4];
            float[] ts = new float[4];
            float sLow = Float.MAX_VALUE, sHigh = -Float.MAX_VALUE, tLow = Float.MAX_VALUE, tHigh = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                int base = k * IQuadTransformer.STRIDE;
                positions[k] = new Vector3f(
                        Float.intBitsToFloat(template[base + IQuadTransformer.POSITION]),
                        Float.intBitsToFloat(template[base + IQuadTransformer.POSITION + 1]),
                        Float.intBitsToFloat(template[base + IQuadTransformer.POSITION + 2]));
                if (worldUv) {
                    ss[k] = 16f * worldS(direction, positions[k]);
                    ts[k] = 16f * worldT(direction, positions[k]);
                } else {
                    // The model's 0-16 UVs span the whole strip, so they map straight onto one tile.
                    ss[k] = 16f * (Float.intBitsToFloat(template[base + IQuadTransformer.UV0]) - u0) / (u1 - u0);
                    ts[k] = 16f * (Float.intBitsToFloat(template[base + IQuadTransformer.UV0 + 1]) - v0) / (v1 - v0);
                }
                sLow = Math.min(sLow, ss[k]);
                sHigh = Math.max(sHigh, ss[k]);
                tLow = Math.min(tLow, ts[k]);
                tHigh = Math.max(tHigh, ts[k]);
            }
            this.sMin = sLow;
            this.sMax = sHigh;
            this.tMin = tLow;
            this.tMax = tHigh;
            float sMid = (sMin + sMax) * 0.5f;
            float tMid = (tMin + tMax) * 0.5f;
            Vector3f tl = null, tr = null, bl = null, br = null;
            for (int k = 0; k < 4; k++) {
                boolean right = ss[k] > sMid;
                boolean bottom = ts[k] > tMid;
                vertexRight[k] = right;
                vertexBottom[k] = bottom;
                if (!right && !bottom) tl = positions[k];
                else if (right && !bottom) tr = positions[k];
                else if (!right) bl = positions[k];
                else br = positions[k];
            }
            this.topLeft = tl;
            this.topRight = tr;
            this.bottomLeft = bl;
            this.bottomRight = br;
            this.axisU = dominantAxis(new Vector3f(topRight).sub(topLeft));
            this.axisV = dominantAxis(new Vector3f(bottomLeft).sub(topLeft));

            for (int corner = 0; corner < 4; corner++) {
                addPiece(corner);
            }
        }

        private void addPiece(int corner) {
            int cu = corner & 1;        // 0 = left column, 1 = right column
            int cv = (corner >> 1) & 1; // 0 = top row, 1 = bottom row
            float sLo = Math.max(sMin, cu * 8f);
            float sHi = Math.min(sMax, cu * 8f + 8f);
            float tLo = Math.max(tMin, cv * 8f);
            float tHi = Math.min(tMax, cv * 8f + 8f);
            if (sHi - sLo < EDGE_SLACK || tHi - tLo < EDGE_SLACK) {
                return;
            }
            boolean reachesU = cu == 0 ? sMin <= EDGE_SLACK : sMax >= 16f - EDGE_SLACK;
            boolean reachesV = cv == 0 ? tMin <= EDGE_SLACK : tMax >= 16f - EDGE_SLACK;
            float sEdge = cu * 16f;
            float tEdge = cv * 16f;
            pieces.add(new Piece(corner, sLo, sHi, tLo, tHi,
                    reachesU ? lookup(horizontal(corner), sEdge, tLo, sEdge, tHi) : null,
                    reachesV ? lookup(vertical(corner), sLo, tEdge, sHi, tEdge) : null,
                    reachesU && reachesV ? lookup(diagonal(corner), sEdge, tEdge, sEdge, tEdge) : null));
        }

        /** The lookup for a neighbour, probed along the tile edge from {@code (s0, t0)} to {@code (s1, t1)}. */
        private Neighbour lookup(Vec3i offset, float s0, float t0, float s1, float t1) {
            if (!matchSurface) {
                return new Neighbour(direction, offset, null);
            }
            return new Neighbour(direction, offset, new Probe(inside(offset, s0, t0), inside(offset, s1, t1)));
        }

        /** The point on the face at {@code (s, t)}, moved just past the shared edge into the neighbour. */
        private Vec3 inside(Vec3i offset, float s, float t) {
            Vector3f point = position(s, t);
            double into = 1 - PROBE_INSET;
            return new Vec3(
                    point.x - offset.getX() * into,
                    point.y - offset.getY() * into,
                    point.z - offset.getZ() * into);
        }

        /** All neighbour lookups this face may query (up to 4 pieces x {H, V, D}). */
        Set<Neighbour> neighbours() {
            Set<Neighbour> lookups = new HashSet<>();
            for (Piece piece : pieces) {
                if (piece.horizontal() != null) lookups.add(piece.horizontal());
                if (piece.vertical() != null) lookups.add(piece.vertical());
                if (piece.diagonal() != null) lookups.add(piece.diagonal());
            }
            return lookups;
        }

        private Vec3i horizontal(int corner) {
            boolean leftColumn = (corner & 1) == 0;
            return leftColumn ? negate(axisU) : axisU;
        }

        private Vec3i vertical(int corner) {
            boolean topRow = (corner >> 1) == 0;
            return topRow ? negate(axisV) : axisV;
        }

        private Vec3i diagonal(int corner) {
            return add(horizontal(corner), vertical(corner));
        }

        void emit(List<BakedQuad> out, @Nullable Map<Neighbour, Boolean> connections) {
            for (Piece piece : pieces) {
                boolean h = connected(connections, piece.horizontal());
                boolean v = connected(connections, piece.vertical());
                boolean d = connected(connections, piece.diagonal());
                out.add(buildPiece(piece, PiecedLayout.tileIndex(h, v, d)));
            }
        }

        private static boolean connected(@Nullable Map<Neighbour, Boolean> connections, @Nullable Neighbour lookup) {
            if (lookup == null) {
                return true;
            }
            return connections != null && connections.getOrDefault(lookup, Boolean.FALSE);
        }

        private BakedQuad buildPiece(Piece piece, int tile) {
            TextureAtlasSprite tileSprite = tiles[tile];
            int[] vertices = new int[template.length];
            for (int k = 0; k < 4; k++) {
                int base = k * IQuadTransformer.STRIDE;
                System.arraycopy(template, base, vertices, base, IQuadTransformer.STRIDE);
                float s = vertexRight[k] ? piece.sHi() : piece.sLo();
                float t = vertexBottom[k] ? piece.tHi() : piece.tLo();

                Vector3f position = position(s, t);
                vertices[base + IQuadTransformer.POSITION] = Float.floatToRawIntBits(position.x);
                vertices[base + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(position.y);
                vertices[base + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(position.z);

                // 1.20.1's getU/getV take pixels, 0-16, where 1.21's take a fraction of the sprite.
                vertices[base + IQuadTransformer.UV0] = Float.floatToRawIntBits(tileSprite.getU(s));
                vertices[base + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(tileSprite.getV(t));
            }
            return new BakedQuad(vertices, tintIndex, direction, tileSprite, shade, hasAmbientOcclusion);
        }

        /** The point on the face at tile pixel {@code (s, t)}. */
        private Vector3f position(float s, float t) {
            float a = sMax > sMin ? (s - sMin) / (sMax - sMin) : 0;
            float b = tMax > tMin ? (t - tMin) / (tMax - tMin) : 0;
            Vector3f top = new Vector3f(topLeft).lerp(topRight, a);
            Vector3f bottom = new Vector3f(bottomLeft).lerp(bottomRight, a);
            return top.lerp(bottom, b);
        }

        /** Vanilla's default horizontal texture coordinate (0-1) for a point on a face pointing that way. */
        private static float worldS(Direction face, Vector3f p) {
            return switch (face) {
                case DOWN, UP, SOUTH -> p.x;
                case NORTH -> 1 - p.x;
                case WEST -> p.z;
                case EAST -> 1 - p.z;
            };
        }

        /** Vanilla's default vertical texture coordinate (0-1) for a point on a face pointing that way. */
        private static float worldT(Direction face, Vector3f p) {
            return switch (face) {
                case DOWN -> 1 - p.z;
                case UP -> p.z;
                default -> 1 - p.y;
            };
        }

        private static Vec3i dominantAxis(Vector3f delta) {
            float ax = Math.abs(delta.x);
            float ay = Math.abs(delta.y);
            float az = Math.abs(delta.z);
            if (ax >= ay && ax >= az) {
                return new Vec3i(Math.signum(delta.x) >= 0 ? 1 : -1, 0, 0);
            }
            if (ay >= az) {
                return new Vec3i(0, Math.signum(delta.y) >= 0 ? 1 : -1, 0);
            }
            return new Vec3i(0, 0, Math.signum(delta.z) >= 0 ? 1 : -1);
        }

        private static Vec3i negate(Vec3i v) {
            return new Vec3i(-v.getX(), -v.getY(), -v.getZ());
        }

        private static Vec3i add(Vec3i a, Vec3i b) {
            return new Vec3i(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
        }
    }
}
