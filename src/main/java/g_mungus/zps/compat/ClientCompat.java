package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

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

    /// A lookup of the moving grid (VS ship or Sable sublevel) drawing the position, or null when
    /// no grid mod is present. The lookup itself answers null while the position is on no grid.
    /// Render thread only.
    public static @Nullable RenderTransformProvider renderTransformAt(Level level, BlockPos pos) {
        if (level instanceof ClientLevel clientLevel) {
            if (Compat.isVSLoaded()) {
                return VSClientCompat.renderTransformAt(clientLevel, pos);
            }
            if (Compat.isSableLoaded()) {
                return SableClientCompat.renderTransformAt(clientLevel, pos);
            }
        }
        return null;
    }

    /// The render transform of the moving grid drawing the position, from the grid's block space
    /// to world space, as it is drawn at partialTick into the current tick (0 the last tick's
    /// pose, 1 this tick's). Null when the position is on no grid. Sable only: null on a Valkyrien
    /// Skies ship, whose interpolation is not exposed this way. Render thread only.
    public static @Nullable Matrix4d renderTransformAt(Level level, BlockPos pos, float partialTick, Matrix4d dest) {
        if (level instanceof ClientLevel && Compat.isSableLoaded() && !Compat.isVSLoaded()) {
            return SableClientCompat.renderTransformAt(pos, partialTick, dest);
        }
        return null;
    }

    /// Unwraps optional moving-grid sound delegates back to the original sound instance.
    public static SoundInstance unwrapMovingSound(SoundInstance instance) {
        if (Compat.isSableLoaded()) {
            return SableClientCompat.unwrapMovingSound(instance);
        }
        return instance;
    }
}
