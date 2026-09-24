package g_mungus.zps.client.reactor;

import g_mungus.zps.compat.ClientCompat;
import g_mungus.zps.compat.RenderTransformProvider;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * A reactor as the client knows it: the shape of its cavity, how hot it is, the glow's mesh once
 * it has been drawn, and the hum while it is hot. Render thread only.
 */
public final class ClientReactor {

    /** Below this the glow is invisible, and the reactor is not drawn at all. */
    public static final float VISIBLE_HEAT = 0.02f;
    /**
     * Fraction of the remaining gap closed each tick, and the most the heat may move in one tick.
     * Together they turn the server's ten-tick steps, and a sputtering reactor's swings, into a
     * slow glide: a jump of one full ignition takes about a second and a half to show.
     */
    private static final float SMOOTHING = 0.08f;
    private static final float MAX_STEP = 0.035f;
    /** Closer to the target than this, the eased heat lands on it. */
    private static final float SETTLE_EPSILON = 1f / 1024f;
    /** How often, in ticks, a built mesh is checked against the walls and light it was built from. */
    private static final int STALE_CHECK_INTERVAL = 20;
    /**
     * The hum starts once the camera is within the first distance of the cavity's centre and ends
     * once it is beyond the second, in blocks: a gap so a player pacing at the edge does not
     * restart it. The first sits just inside the sound's attenuation distance, where it would be
     * silent anyway.
     */
    private static final double HUM_START_DISTANCE = 30;
    private static final double HUM_END_DISTANCE = 40;

    private final int id;
    private final VoxelShape shape;
    /** The cavity's lowest corner: the origin of the frame the glow is drawn in. */
    private final BlockPos origin;
    private final BlockPos host;
    /** Looks up the moving grid the reactor is on, by position; null when no grid mod is present. */
    private final @Nullable RenderTransformProvider grid;
    /** The glow's coat. Built on first use, and let go when the walls may have changed. */
    private @Nullable ReactorGlowMesh glowMesh;
    /** The hum, while the reactor is hot and the camera near; null otherwise. */
    private @Nullable ReactorHumSound hum;
    private final Matrix4d gridScratch = new Matrix4d();
    private int ticks;

    /** What the server last said, over ignition temperature. */
    private float targetHeat;
    /** What is being drawn, eased toward the target so packets every ten ticks do not step. */
    private float displayHeat;

    public ClientReactor(ClientLevel level, int id, VoxelShape shape, float heat) {
        this.id = id;
        this.shape = shape;
        this.origin = CavityShapes.origin(shape);
        this.host = CavityShapes.lowestCell(shape);
        this.targetHeat = heat;
        this.displayHeat = heat;
        this.grid = ClientCompat.renderTransformAt(level, host);
    }

    /**
     * Which faces of which cells are against a wall: for every cell with an open side, a bit per
     * {@link Direction} ordinal. Cells deep inside the cavity have no open side and are absent.
     */
    static Long2IntMap faceMasks(DiscreteVoxelShape grid, BlockPos origin) {
        Long2IntMap masks = new Long2IntOpenHashMap();
        grid.forAllFaces((direction, x, y, z) -> masks.mergeInt(
                BlockPos.asLong(origin.getX() + x, origin.getY() + y, origin.getZ() + z),
                1 << direction.ordinal(), (a, b) -> a | b));
        return masks;
    }

    /**
     * How far the cavity runs back from each face of a cell: for the face on side {@code d},
     * the number of consecutive cells from this one heading away from {@code d}, this one included.
     * Packed a byte per {@link Direction} ordinal into a long, capped at 255.
     */
    static long faceDepths(DiscreteVoxelShape grid, BlockPos origin, int x, int y, int z) {
        long packed = 0;
        for (Direction direction : Direction.values()) {
            Direction inward = direction.getOpposite();
            int depth = 0;
            int cx = x - origin.getX(), cy = y - origin.getY(), cz = z - origin.getZ();
            while (depth < 255 && isFull(grid, cx, cy, cz)) {
                depth++;
                cx += inward.getStepX();
                cy += inward.getStepY();
                cz += inward.getStepZ();
            }
            packed |= (long) depth << (8 * direction.ordinal());
        }
        return packed;
    }

    private static boolean isFull(DiscreteVoxelShape grid, int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0
                && x < grid.getSize(Direction.Axis.X) && y < grid.getSize(Direction.Axis.Y) && z < grid.getSize(Direction.Axis.Z)
                && grid.isFull(x, y, z);
    }

    public int id() {
        return id;
    }

    public VoxelShape shape() {
        return shape;
    }

    public BlockPos origin() {
        return origin;
    }

    /** The chunk the server keys this reactor to: the one holding its lowest interior cell. */
    public ChunkPos hostChunk() {
        return new ChunkPos(host);
    }

    /** A stable 0..1 noise phase from the reactor id, so two reactors side by side do not match. */
    public float seed() {
        long h = (id + 1L) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (h & 0xFFFF) / 65536f;
    }

    /**
     * Where the moving grid the reactor rides is drawn this frame, as the map from the grid's own
     * block space to the world, written into dest; null on the ground.
     *
     * <p>Asked every frame rather than remembered: on a rejoin the reactor packet can land before
     * Sable has the sublevel it sits on, and the answer changes when the sublevel turns up.
     */
    public @Nullable Matrix4d gridTransform(Matrix4d dest) {
        return grid == null ? null : grid.localToWorld(dest);
    }

    /**
     * The glow's coat, built from the walls as they stand in the level the first time it is asked
     * for. Null for a shape with nothing to coat.
     */
    @Nullable ReactorGlowMesh glowMesh(ClientLevel level) {
        if (glowMesh == null) {
            glowMesh = ReactorGlowMesh.build(shape, level);
        }
        return glowMesh;
    }

    /** Frees the coat; the next {@link #glowMesh} builds it afresh. */
    void releaseGlowMesh() {
        if (glowMesh != null) {
            glowMesh.close();
            glowMesh = null;
        }
    }

    /** Lets go of everything held for this reactor: the client is done with it. */
    void release() {
        releaseGlowMesh();
        stopHum();
    }

    private void stopHum() {
        if (hum != null) {
            hum.end();
            hum = null;
        }
    }

    /** The middle of the cavity in world space, wherever the grid it may ride has taken it. */
    Vec3 centre() {
        Vec3 centre = shape.bounds().getCenter();
        Matrix4d grid = gridTransform(gridScratch);
        if (grid == null) {
            return centre;
        }
        Vector3d moved = grid.transformPosition(new Vector3d(centre.x, centre.y, centre.z));
        return new Vec3(moved.x, moved.y, moved.z);
    }

    public float targetHeat() {
        return targetHeat;
    }

    public void setTargetHeat(float heat) {
        targetHeat = heat;
    }

    public float displayHeat() {
        return displayHeat;
    }

    /**
     * Once a client tick: eases the heat, keeps the hum going while it is wanted, and every so
     * often throws the mesh away if the walls it was cut against, or the light baked into its
     * windows, are not what they were.
     */
    void tick(ClientLevel level) {
        tickHeat();
        tickHum();
        if (glowMesh != null && ++ticks % STALE_CHECK_INTERVAL == 0 && glowMesh.isStale(level)) {
            releaseGlowMesh();
        }
    }

    /**
     * Starts the hum when the reactor is hot and the camera near, and ends it when either stops
     * being so. Also ended while block sounds are turned down, since the engine drops a muted
     * channel without a word and would never bring it back.
     */
    private void tickHum() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean audible = ZPSConfig.playReactorHum()
                && minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0f
                && minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS) > 0f;
        double distance = Double.POSITIVE_INFINITY;
        if (audible && displayHeat >= VISIBLE_HEAT) {
            distance = centre().distanceTo(minecraft.gameRenderer.getMainCamera().getPosition());
        }
        SoundManager sounds = minecraft.getSoundManager();
        if (hum != null && (distance > HUM_END_DISTANCE || !sounds.isActive(hum))) {
            stopHum();
        }
        if (hum == null && distance <= HUM_START_DISTANCE) {
            hum = new ReactorHumSound(this);
            sounds.play(hum);
        }
    }

    /** Eases the drawn heat toward the server's figure. */
    private void tickHeat() {
        float step = (targetHeat - displayHeat) * SMOOTHING;
        displayHeat += Math.max(-MAX_STEP, Math.min(MAX_STEP, step));
        if (Math.abs(targetHeat - displayHeat) < SETTLE_EPSILON) {
            displayHeat = targetHeat;
        }
    }
}
