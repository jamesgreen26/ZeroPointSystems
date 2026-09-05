package g_mungus.zps.reactor;

import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.config.ZPSConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.kelvin.api.DuctNodePos;

import java.util.ArrayList;
import java.util.List;

/**
 * One sealed reactor: its geometry, the ratings that follow from it, and the little state the
 * manager tracks between ticks. Everything about the gas lives in Kelvin, at {@link #host()}.
 */
public final class Reactor {

    private final int id;
    private final LongSet interior;
    private final LongList walls;
    private final BlockPos host;

    private final int volume;
    private final int wallCount;
    private final double compactness;
    private final double burstPressure;
    private final double wallHeatCapacity;

    private boolean ignitedOnce;
    private boolean lit;

    /** Chamber contents read from disk, applied once the node exists again. */
    private @Nullable CompoundTag savedChamber;

    /** Heat last sent to clients; not persisted, so a reload resends. */
    private float lastSentHeat = -1f;

    /** The cavity as a shape, built on first use. */
    private @Nullable VoxelShape shape;

    // FE moved by the exchangers, for the debug readout. Rolled over every tick.
    private int feInThisTick;
    private int feOutThisTick;
    private int feInLastTick;
    private int feOutLastTick;

    public Reactor(int id, CavityScan scan) {
        this(id, scan.interior(), new LongArrayList(scan.walls()), scan.host(), null, false);
    }

    private Reactor(int id, LongSet interior, LongList walls, BlockPos host,
                    @Nullable CompoundTag savedChamber, boolean ignitedOnce) {
        this.id = id;
        this.interior = interior;
        this.walls = walls;
        this.host = host;
        this.savedChamber = savedChamber;
        this.ignitedOnce = ignitedOnce;

        this.volume = interior.size();
        this.wallCount = walls.size();
        this.compactness = ReactorGeometry.compactness(volume, wallCount);
        this.burstPressure = ReactorGeometry.burstPressure(volume, wallCount,
                ZPSConfig.burstBasePressurePa(),
                ZPSConfig.burstCompactnessExponent(),
                ZPSConfig.burstSizeExponent());
        this.wallHeatCapacity = ReactorGeometry.wallHeatCapacity(wallCount, ZPSConfig.reactorWallHeatCapacityJPerK());
    }

    // --- geometry ----------------------------------------------------------------------------

    public int id() {
        return id;
    }

    public LongSet interior() {
        return interior;
    }

    public LongList walls() {
        return walls;
    }

    /** The cavity as a whole-block shape in world coordinates. */
    public VoxelShape shape() {
        if (shape == null) {
            shape = CavityShapes.fromCells(interior);
        }
        return shape;
    }

    /** The interior cell the chamber's Kelvin node sits at. */
    public BlockPos host() {
        return host;
    }

    public DuctNodePos hostNodePos(ServerLevel level) {
        return GasEdgeNegotiator.nodePos(level, host);
    }

    public int volume() {
        return volume;
    }

    public int wallCount() {
        return wallCount;
    }

    public double compactness() {
        return compactness;
    }

    public double burstPressure() {
        return burstPressure;
    }

    public double wallHeatCapacity() {
        return wallHeatCapacity;
    }

    public boolean isWall(BlockPos pos) {
        return walls.contains(pos.asLong());
    }

    public boolean isInterior(BlockPos pos) {
        return interior.contains(pos.asLong());
    }

    /**
     * Whether a functional wall block at {@code pos} faces out of this reactor: its inner face,
     * opposite {@code facing}, must be on the cavity. One that does not is part of the shell but
     * does nothing.
     */
    public boolean isOriented(BlockPos pos, Direction facing) {
        return isWall(pos) && isInterior(pos.relative(facing.getOpposite()));
    }

    public BlockPos randomWall(RandomSource random) {
        return BlockPos.of(walls.getLong(random.nextInt(walls.size())));
    }

    /** The wall positions currently holding the given block. */
    public List<BlockPos> wallsOf(ServerLevel level, Block block) {
        List<BlockPos> found = new ArrayList<>();
        for (int i = 0; i < walls.size(); i++) {
            BlockPos pos = BlockPos.of(walls.getLong(i));
            if (level.getBlockState(pos).is(block)) {
                found.add(pos);
            }
        }
        return found;
    }

    /** The wall positions holding the given block and facing out of this reactor. */
    public List<BlockPos> orientedWallsOf(ServerLevel level, Block block) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : wallsOf(level, block)) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.FACING)
                    && isOriented(pos, state.getValue(BlockStateProperties.FACING))) {
                found.add(pos);
            }
        }
        return found;
    }

    // --- state -------------------------------------------------------------------------------

    public boolean hasIgnited() {
        return ignitedOnce;
    }

    void markIgnited() {
        ignitedOnce = true;
    }

    /** Chamber at or above ignition temperature. */
    public boolean isLit() {
        return lit;
    }

    void setLit(boolean lit) {
        this.lit = lit;
    }

    @Nullable CompoundTag takeSavedChamber() {
        CompoundTag tag = savedChamber;
        savedChamber = null;
        return tag;
    }

    void setSavedChamber(@Nullable CompoundTag tag) {
        savedChamber = tag;
    }

    @Nullable CompoundTag savedChamber() {
        return savedChamber;
    }

    float lastSentHeat() {
        return lastSentHeat;
    }

    void setLastSentHeat(float heat) {
        lastSentHeat = heat;
    }

    public void recordFeIn(int fe) {
        feInThisTick += fe;
    }

    public void recordFeOut(int fe) {
        feOutThisTick += fe;
    }

    void rollTickCounters() {
        feInLastTick = feInThisTick;
        feOutLastTick = feOutThisTick;
        feInThisTick = 0;
        feOutThisTick = 0;
    }

    public int feInLastTick() {
        return feInLastTick;
    }

    public int feOutLastTick() {
        return feOutLastTick;
    }

    // --- persistence -------------------------------------------------------------------------

    CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putLong("Host", host.asLong());
        tag.putLongArray("Interior", interior.toLongArray());
        tag.putLongArray("Walls", walls.toLongArray());
        tag.putBoolean("IgnitedOnce", ignitedOnce);
        if (savedChamber != null) {
            tag.put("Chamber", savedChamber);
        }
        return tag;
    }

    static Reactor fromNbt(CompoundTag tag) {
        return new Reactor(
                tag.getInt("Id"),
                new LongOpenHashSet(tag.getLongArray("Interior")),
                new LongArrayList(tag.getLongArray("Walls")),
                BlockPos.of(tag.getLong("Host")),
                tag.contains("Chamber") ? tag.getCompound("Chamber") : null,
                tag.getBoolean("IgnitedOnce"));
    }
}
