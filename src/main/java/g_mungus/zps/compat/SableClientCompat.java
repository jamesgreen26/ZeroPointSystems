package g_mungus.zps.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

import java.util.ArrayList;
import java.util.List;

public final class SableClientCompat {

    private SableClientCompat() {
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSClientCompat#isOnShip.
    static boolean isInSubLevel(ClientLevel level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContainingClient(pos.getCenter()) != null;
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSClientCompat#toWorldRenderPos: maps a sublevel position to its
    /// world-space render position using the interpolated render pose.
    static Vec3 toWorldRenderPos(ClientLevel level, Vec3 pos) {
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(pos);
        if (subLevel == null) return pos;
        return subLevel.renderPose().transformPosition(pos);
    }

    /// Only call after verifying that Sable is loaded.
    /// Sable equivalent of VSClientCompat#toLocalRenderSpaceOf: transforms pos (in its own
    /// sublevel's coordinates) into the local render space of the sublevel managing anchorPos.
    static Vec3 toLocalRenderSpaceOf(ClientLevel level, BlockPos anchorPos, Vec3 pos) {
        Vec3 worldPos = toWorldRenderPos(level, pos);
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(anchorPos.getCenter());
        if (subLevel == null) return worldPos;
        return subLevel.renderPose().transformPositionInverse(worldPos);
    }

    /// Only call after verifying that Sable is loaded.
    /// The render transform of whichever sublevel contains pos when asked. The sublevel is looked
    /// up on every call rather than captured: on a rejoin the blocks can arrive before the sublevel
    /// does, and Sable may replace its client object when the sublevel leaves and re-enters range.
    static RenderTransformProvider renderTransformAt(ClientLevel level, BlockPos pos) {
        Vec3 center = pos.getCenter();
        return dest -> {
            ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(center);
            return subLevel == null ? null : subLevel.renderPose().bakeIntoMatrix(dest);
        };
    }

    /// Only call after verifying that Sable is loaded.
    /// The block space to world transform of the sublevel containing pos as Sable draws it at
    /// partialTick into the current tick: 0 is the last tick's pose, 1 this tick's. Null while pos
    /// is on no sublevel. Baked straight away, as renderPose hands back a pose it reuses.
    static @Nullable Matrix4d renderTransformAt(BlockPos pos, float partialTick, Matrix4d dest) {
        ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(pos.getCenter());
        return subLevel == null ? null : subLevel.renderPose(partialTick).bakeIntoMatrix(dest);
    }

    /// Only call after verifying that Sable is loaded.
    /// Every sublevel reaching into worldBounds, with its block space to world transform as drawn
    /// at each of the partial ticks. The sublevels are gathered before any pose is read, as Sable
    /// does not want its bounds touched mid-iteration.
    static List<ClientCompat.GridPoses> gridsTouching(ClientLevel level, AABB worldBounds, float[] partialTicks) {
        List<ClientSubLevelAccess> found = new ArrayList<>();
        for (SubLevelAccess subLevel : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(worldBounds))) {
            if (subLevel instanceof ClientSubLevelAccess client) {
                found.add(client);
            }
        }
        List<ClientCompat.GridPoses> grids = new ArrayList<>(found.size());
        for (ClientSubLevelAccess subLevel : found) {
            BoundingBox3dc bounds = subLevel.boundingBox();
            Matrix4d[] poses = new Matrix4d[partialTicks.length];
            for (int i = 0; i < partialTicks.length; i++) {
                poses[i] = subLevel.renderPose(partialTicks[i]).bakeIntoMatrix(new Matrix4d());
            }
            grids.add(new ClientCompat.GridPoses(subLevel.getUniqueId(),
                    new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()), poses));
        }
        return grids;
    }

    /// Only call after verifying that Sable is loaded.
    /// Returns the original sound instance wrapped by Sable's moving sound delegate.
    static SoundInstance unwrapMovingSound(SoundInstance instance) {
        if (instance instanceof MovingSoundInstanceDelegate delegate) {
            return delegate.instance;
        }
        return instance;
    }
}
