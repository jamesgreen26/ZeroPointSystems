package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ClientCompat {

    /// True when the position is managed by a ship (VS) or sublevel (Sable).
    public static boolean isOnShip(Level level, BlockPos pos) {
        if (level instanceof ClientLevel clientLevel) {
            if (Compat.isVSLoaded()) {
                return VSClientCompat.isOnShip(clientLevel, pos);
            }
            if (Compat.isSableLoaded()) {
                return SableClientCompat.isInSubLevel(clientLevel, pos);
            }
        }
        return false;
    }

    /// Maps a shipyard/sublevel position to its world-space render position. Returns the input
    /// unchanged when neither VS nor Sable is present or the position is not managed.
    public static Vec3 toWorldRenderPos(Level level, Vec3 pos) {
        if (level instanceof ClientLevel clientLevel) {
            if (Compat.isVSLoaded()) {
                return VSClientCompat.toWorldRenderPos(clientLevel, pos);
            }
            if (Compat.isSableLoaded()) {
                return SableClientCompat.toWorldRenderPos(clientLevel, pos);
            }
        }
        return pos;
    }

    /// Transforms pos (in its own grid's coordinates) into the local space of the grid managing
    /// anchorPos, using render transforms. Identity when neither VS nor Sable is present.
    public static Vec3 toLocalRenderSpaceOf(Level level, BlockPos anchorPos, Vec3 pos) {
        if (level instanceof ClientLevel clientLevel) {
            if (Compat.isVSLoaded()) {
                return VSClientCompat.toLocalRenderSpaceOf(clientLevel, anchorPos, pos);
            }
            if (Compat.isSableLoaded()) {
                return SableClientCompat.toLocalRenderSpaceOf(clientLevel, anchorPos, pos);
            }
        }
        return pos;
    }

    /// Unwraps optional moving-grid sound delegates back to the original sound instance.
    public static SoundInstance unwrapMovingSound(SoundInstance instance) {
        if (Compat.isSableLoaded()) {
            return SableClientCompat.unwrapMovingSound(instance);
        }
        return instance;
    }
}
