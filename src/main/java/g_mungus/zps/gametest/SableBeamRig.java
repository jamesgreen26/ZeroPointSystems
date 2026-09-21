package g_mungus.zps.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import g_mungus.zps.blockentity.BeamCollectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector2i;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Tractor Beam's sublevel tests need from Sable itself, kept in a class of its own so that the
 * test class can be loaded, and its tests skipped, where Sable is not installed.
 * <p>
 * A rig is a handful of blocks lifted out of the world into a sublevel, held still, and posed. "Held still" is
 * Sable's physics being paused for the whole level: the same state a player gets from {@code /sable physics
 * paused true}, which is how a sublevel is normally frozen in place to be worked on.
 */
final class SableBeamRig {
    private final ServerLevel level;
    private final ServerSubLevelContainer container;
    private final ServerSubLevel subLevel;

    /** The plot's extent as it was assembled; Sable's own shrinks as blocks leave, and goes with the sublevel. */
    private final BlockPos plotMin;
    private final BlockPos plotMax;

    private SableBeamRig(ServerLevel level, ServerSubLevelContainer container, ServerSubLevel subLevel) {
        this.level = level;
        this.container = container;
        this.subLevel = subLevel;
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        this.plotMin = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        this.plotMax = new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    /** Every block still on the sublevel, as positions in its plot. */
    List<BlockPos> blocksLeft() {
        List<BlockPos> left = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(plotMin, plotMax)) {
            if (!level.getBlockState(pos).isAir()) {
                left.add(pos.immutable());
            }
        }
        return left;
    }

    /**
     * Lifts {@code blocks}, which lie within {@code min} to {@code max} inclusive, into a sublevel, and turns it
     * by {@code rotation} about the middle of that box, where it stays.
     */
    static SableBeamRig assemble(ServerLevel level, BlockPos min, BlockPos max, List<BlockPos> blocks,
                                 Quaterniond rotation) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            throw new IllegalStateException("This level has no sublevel container");
        }
        // Before anything exists to fall.
        container.physicsSystem().setPaused(true);

        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, min, blocks,
                new BoundingBox3i(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
        container.physicsSystem().getPipeline().teleport(subLevel,
                new Vector3d(
                        min.getX() + (1 + max.getX() - min.getX()) / 2.0,
                        min.getY() + (1 + max.getY() - min.getY()) / 2.0,
                        min.getZ() + (1 + max.getZ() - min.getZ()) / 2.0),
                rotation);
        return new SableBeamRig(level, container, subLevel);
    }

    /** The panel's controller, wherever in the sublevel's plot the panel ended up; null until it has formed. */
    @Nullable
    BeamCollectorBlockEntity findController(int width) {
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (level.getBlockEntity(pos) instanceof BeamCollectorBlockEntity beam
                    && beam.isController() && beam.getWidth() == width) {
                return beam;
            }
        }
        return null;
    }

    /** Where the sublevel is and how it is turned, for saying so when a test fails. */
    String describePose() {
        Pose3dc pose = subLevel.logicalPose();
        return "position " + pose.position() + ", orientation " + pose.orientation();
    }

    void remove() {
        // Sable removes a sublevel by itself once its last block has gone.
        if (subLevel.isRemoved()) {
            return;
        }
        LevelPlot plot = subLevel.getPlot();
        Vector2i origin = container.getOrigin();
        container.removeSubLevel(plot.plotPos.x - origin.x, plot.plotPos.z - origin.y, SubLevelRemovalReason.REMOVED);
    }
}
