package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;

public class ClientCompat {

    /// True when VS is loaded and the position is managed by a ship.
    public static boolean isOnShip(Level level, BlockPos pos) {
        if (level instanceof ClientLevel clientLevel && Compat.isVSLoaded()) {
            return VSClientCompat.isOnShip(clientLevel, pos);
        }
        return false;
    }

    /// Maps a shipyard position to its world-space render position. Returns the input unchanged
    /// when VS is absent or the position is not managed by a ship.
    public static Vec3 toWorldRenderPos(Level level, Vec3 pos) {
        if (level instanceof ClientLevel clientLevel && Compat.isVSLoaded()) {
            return VSClientCompat.toWorldRenderPos(clientLevel, pos);
        }
        return pos;
    }

    /// Transforms pos (in its own grid's coordinates) into the local space of the grid managing
    /// anchorPos, using render transforms. Identity when VS is absent.
    public static Vec3 toLocalRenderSpaceOf(Level level, BlockPos anchorPos, Vec3 pos) {
        if (level instanceof ClientLevel clientLevel && Compat.isVSLoaded()) {
            return VSClientCompat.toLocalRenderSpaceOf(clientLevel, anchorPos, pos);
        }
        return pos;
    }

    /// A lookup of the moving grid (VS ship) drawing the position, or null when VS is not
    /// present. The lookup itself answers null while the position is on no ship.
    /// Render thread only.
    public static @Nullable RenderTransformProvider renderTransformAt(Level level, BlockPos pos) {
        if (level instanceof ClientLevel clientLevel && Compat.isVSLoaded()) {
            return VSClientCompat.renderTransformAt(clientLevel, pos);
        }
        return null;
    }

    /// The render transform of the moving grid drawing the position, from the grid's block space
    /// to world space, as it is drawn at partialTick into the current tick (0 the last tick's
    /// pose, 1 this tick's). Sable only, so always null here: a Valkyrien Skies ship's
    /// interpolation is not exposed this way. Render thread only.
    public static @Nullable Matrix4d renderTransformAt(Level level, BlockPos pos, float partialTick, Matrix4d dest) {
        return null;
    }
}
