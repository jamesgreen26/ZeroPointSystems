package g_mungus.zps.client.reactor;

import g_mungus.zps.reactor.CavityShapes;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A reactor as the client knows it: which of its cells touch a wall, on which sides, and how hot
 * it is. Immutable apart from the two heat values, which Flywheel's worker threads read while
 * packets on the render thread write them.
 */
public final class ClientReactor {

    private final int id;
    private final VoxelShape shape;
    private final BlockPos host;
    /** Cells with at least one open face, as packed positions. */
    private final long[] cells;
    /** For each of {@link #cells}, a bit per {@link Direction} ordinal set where that face is a wall. */
    private final int[] faces;
    private final ReactorEffect effect;

    /** What the server last said, over ignition temperature. */
    private volatile float targetHeat;
    /** What is being drawn, eased toward the target so packets every ten ticks do not step. */
    private volatile float displayHeat;

    public ClientReactor(ClientLevel level, int id, VoxelShape shape, float heat) {
        this.id = id;
        this.shape = shape;
        this.host = CavityShapes.lowestCell(shape);
        Long2IntMap masks = faceMasks(shape);
        this.cells = new long[masks.size()];
        this.faces = new int[masks.size()];
        int n = 0;
        for (Long2IntMap.Entry entry : masks.long2IntEntrySet()) {
            cells[n] = entry.getLongKey();
            faces[n] = entry.getIntValue();
            n++;
        }
        this.targetHeat = heat;
        this.displayHeat = heat;
        this.effect = new ReactorEffect(level, this);
    }

    /**
     * Which faces of which cells are against a wall: for every cell with an open side, a bit per
     * {@link Direction} ordinal. Cells deep inside the cavity have no open side and are absent.
     */
    static Long2IntMap faceMasks(VoxelShape shape) {
        return faceMasks(CavityShapes.grid(shape), CavityShapes.origin(shape));
    }

    static Long2IntMap faceMasks(DiscreteVoxelShape grid, BlockPos origin) {
        Long2IntMap masks = new Long2IntOpenHashMap();
        grid.forAllFaces((direction, x, y, z) -> masks.mergeInt(
                BlockPos.asLong(origin.getX() + x, origin.getY() + y, origin.getZ() + z),
                1 << direction.ordinal(), (a, b) -> a | b));
        return masks;
    }

    public int id() {
        return id;
    }

    public VoxelShape shape() {
        return shape;
    }

    /** The cells that draw: those with a wall on at least one side. */
    public long[] cells() {
        return cells;
    }

    public int[] faces() {
        return faces;
    }

    public ReactorEffect effect() {
        return effect;
    }

    /** The chunk the server keys this reactor to: the one holding its lowest interior cell. */
    public ChunkPos hostChunk() {
        return new ChunkPos(host);
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

    public void setDisplayHeat(float heat) {
        displayHeat = heat;
    }
}
