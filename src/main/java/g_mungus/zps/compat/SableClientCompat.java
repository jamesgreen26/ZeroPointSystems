package g_mungus.zps.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

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
    /// Returns the original sound instance wrapped by Sable's moving sound delegate.
    static SoundInstance unwrapMovingSound(SoundInstance instance) {
        if (instance instanceof MovingSoundInstanceDelegate delegate) {
            return delegate.instance;
        }
        return instance;
    }
}
