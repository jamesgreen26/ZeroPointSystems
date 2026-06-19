package g_mungus.zps.client.model.connected;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
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

public class ConnectingBakedModel extends BakedModelWrapper<BakedModel> {
    /** Maps each relevant neighbour offset to whether it connects, for the block being rendered. */
    public static final ModelProperty<Map<Vec3i, Boolean>> CONNECTIONS = new ModelProperty<>();

    private static final int SIDE_COUNT = 7; // 6 directions + 1 for the null (unculled) bucket

    private final List<List<BakedQuad>> staticBySide;
    private final List<List<ConnectingFace>> connectingBySide;
    private final List<List<BakedQuad>> itemBySide;
    private final BakedModel itemModel;
    private final List<ConnectionRule> rules;
    private final Set<Vec3i> neighbourOffsets;
    private final RenderTypeGroup renderTypes;

    public ConnectingBakedModel(BakedModel original, List<ConnectionRule> rules, BlockState owner, RandomSource rand,
                                RenderTypeGroup renderTypes) {
        super(original);
        this.rules = rules;
        this.renderTypes = renderTypes;
        this.staticBySide = new ArrayList<>(SIDE_COUNT);
        this.connectingBySide = new ArrayList<>(SIDE_COUNT);
        this.itemBySide = new ArrayList<>(SIDE_COUNT);
        for (int i = 0; i < SIDE_COUNT; i++) {
            staticBySide.add(new ArrayList<>());
            connectingBySide.add(new ArrayList<>());
            itemBySide.add(new ArrayList<>());
        }
        Set<Vec3i> offsets = new HashSet<>();

        sortQuads(original.getQuads(owner, null, rand), SIDE_COUNT - 1, offsets);
        for (Direction side : Direction.values()) {
            sortQuads(original.getQuads(owner, side, rand), side.get3DDataValue(), offsets);
        }

        this.neighbourOffsets = offsets;
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

    private void sortQuads(List<BakedQuad> quads, int sideIndex, Set<Vec3i> offsets) {
        for (BakedQuad quad : quads) {
            ConnectedTextureMeta.Meta meta = ConnectedTextureMeta.get(quad.getSprite().contents().name()).orElse(null);
            int tileCount = quad.getSprite().contents().width() / quad.getSprite().contents().height();
            if (meta != null && meta.pieced() && tileCount >= PiecedLayout.TILE_COUNT) {
                ConnectingFace face = new ConnectingFace(quad, tileCount);
                connectingBySide.get(sideIndex).add(face);
                face.emit(itemBySide.get(sideIndex), null);
                offsets.addAll(face.neighbourOffsets());
            } else {
                staticBySide.get(sideIndex).add(quad);
                itemBySide.get(sideIndex).add(quad);
            }
        }
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
        if (neighbourOffsets.isEmpty()) {
            return modelData;
        }
        Map<Vec3i, Boolean> connections = new HashMap<>(neighbourOffsets.size());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Vec3i offset : neighbourOffsets) {
            cursor.setWithOffset(pos, offset.getX(), offset.getY(), offset.getZ());
            connections.put(offset, ConnectionRule.anyMatch(rules, state, level.getBlockState(cursor)));
        }
        return modelData.derive().with(CONNECTIONS, connections).build();
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

    private List<BakedQuad> collect(@Nullable Direction side, @Nullable Map<Vec3i, Boolean> connections) {
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
     * Pre-computed geometry for one connecting face. Holds the four texture-space corner positions, per-vertex
     * texture-space coordinates (to preserve winding), the world-space neighbour directions for the texture axes,
     * and a template vertex array used to copy colour/light/normal data into the generated sub-quads.
     */
    private static final class ConnectingFace {
        private final int[] template; // original 4-vertex data; position+uv are overwritten per sub-quad
        private final TextureAtlasSprite sprite;
        private final int tintIndex;
        private final Direction direction;
        private final boolean shade;
        private final boolean hasAmbientOcclusion;
        private final int tileCount;
        private final float u0;
        private final float u1;
        private final float v0;
        private final float v1;
        private final int spriteWidth;
        private final int spriteHeight;
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

        ConnectingFace(BakedQuad quad, int tileCount) {
            this.template = quad.getVertices().clone();
            this.sprite = quad.getSprite();
            this.tintIndex = quad.getTintIndex();
            this.direction = quad.getDirection();
            this.shade = quad.isShade();
            this.hasAmbientOcclusion = quad.hasAmbientOcclusion();
            this.tileCount = tileCount;
            this.u0 = sprite.getU0();
            this.u1 = sprite.getU1();
            this.v0 = sprite.getV0();
            this.v1 = sprite.getV1();
            this.spriteWidth = sprite.contents().width();
            this.spriteHeight = sprite.contents().height();

            Vector3f[] positions = new Vector3f[4];
            float[] us = new float[4];
            float[] vs = new float[4];
            float uMin = Float.MAX_VALUE, uMax = -Float.MAX_VALUE, vMin = Float.MAX_VALUE, vMax = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                int base = k * IQuadTransformer.STRIDE;
                positions[k] = new Vector3f(
                        Float.intBitsToFloat(template[base + IQuadTransformer.POSITION]),
                        Float.intBitsToFloat(template[base + IQuadTransformer.POSITION + 1]),
                        Float.intBitsToFloat(template[base + IQuadTransformer.POSITION + 2]));
                us[k] = Float.intBitsToFloat(template[base + IQuadTransformer.UV0]);
                vs[k] = Float.intBitsToFloat(template[base + IQuadTransformer.UV0 + 1]);
                uMin = Math.min(uMin, us[k]);
                uMax = Math.max(uMax, us[k]);
                vMin = Math.min(vMin, vs[k]);
                vMax = Math.max(vMax, vs[k]);
            }
            float uMid = (uMin + uMax) * 0.5f;
            float vMid = (vMin + vMax) * 0.5f;
            Vector3f tl = null, tr = null, bl = null, br = null;
            for (int k = 0; k < 4; k++) {
                boolean right = us[k] > uMid;
                boolean bottom = vs[k] > vMid;
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
        }

        /** All neighbour offsets this face may query (4 corners x {H, V, D}). */
        Set<Vec3i> neighbourOffsets() {
            Set<Vec3i> offsets = new HashSet<>();
            for (int corner = 0; corner < 4; corner++) {
                offsets.add(horizontal(corner));
                offsets.add(vertical(corner));
                offsets.add(diagonal(corner));
            }
            return offsets;
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

        void emit(List<BakedQuad> out, @Nullable Map<Vec3i, Boolean> connections) {
            for (int corner = 0; corner < 4; corner++) {
                boolean h = connected(connections, horizontal(corner));
                boolean v = connected(connections, vertical(corner));
                boolean d = connected(connections, diagonal(corner));
                int tile = PiecedLayout.tileIndex(h, v, d);
                out.add(buildCorner(corner, tile));
            }
        }

        private static boolean connected(@Nullable Map<Vec3i, Boolean> connections, Vec3i offset) {
            return connections != null && connections.getOrDefault(offset, Boolean.FALSE);
        }

        private BakedQuad buildCorner(int corner, int tile) {
            int cu = corner & 1;        // 0 = left column, 1 = right column
            int cv = (corner >> 1) & 1; // 0 = top row, 1 = bottom row
            int[] vertices = new int[template.length];
            for (int k = 0; k < 4; k++) {
                int base = k * IQuadTransformer.STRIDE;
                System.arraycopy(template, base, vertices, base, IQuadTransformer.STRIDE);
                int sk = vertexRight[k] ? 1 : 0;
                int tk = vertexBottom[k] ? 1 : 0;

                float a = (cu + sk) * 0.5f;
                float b = (cv + tk) * 0.5f;
                Vector3f position = bilinear(a, b);
                vertices[base + IQuadTransformer.POSITION] = Float.floatToRawIntBits(position.x);
                vertices[base + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(position.y);
                vertices[base + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(position.z);

                float pixelU = 16f * tile + cu * 8f + sk * 8f;
                float pixelV = cv * 8f + tk * 8f;
                float atlasU = u0 + (pixelU / spriteWidth) * (u1 - u0);
                float atlasV = v0 + (pixelV / spriteHeight) * (v1 - v0);
                vertices[base + IQuadTransformer.UV0] = Float.floatToRawIntBits(atlasU);
                vertices[base + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(atlasV);
            }
            return new BakedQuad(vertices, tintIndex, direction, sprite, shade, hasAmbientOcclusion);
        }

        private Vector3f bilinear(float a, float b) {
            Vector3f top = new Vector3f(topLeft).lerp(topRight, a);
            Vector3f bottom = new Vector3f(bottomLeft).lerp(bottomRight, a);
            return top.lerp(bottom, b);
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
