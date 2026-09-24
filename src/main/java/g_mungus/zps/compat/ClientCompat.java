package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
}
