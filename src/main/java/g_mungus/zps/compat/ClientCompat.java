package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ClientCompat {

    /// True when VS is loaded and the position is managed by a ship.
    public static boolean isOnShip(Level level, BlockPos pos) {
        if (Compat.isVSLoaded() && level instanceof ClientLevel clientLevel) {
            return VSClientCompat.isOnShip(clientLevel, pos);
        }
        return false;
    }

    /// Maps a shipyard position to its world-space render position. Returns the input unchanged
    /// when VS is absent or the position is not managed by a ship.
    public static Vec3 toWorldRenderPos(ClientLevel level, Vec3 pos) {
        if (Compat.isVSLoaded()) {
            return VSClientCompat.toWorldRenderPos(level, pos);
        }
        return pos;
    }

    /// Transforms pos (in its own grid's coordinates) into the local space of the grid managing
    /// anchorPos, using render transforms. Identity when VS is absent.
    public static Vec3 toLocalRenderSpaceOf(ClientLevel level, BlockPos anchorPos, Vec3 pos) {
        if (Compat.isVSLoaded()) {
            return VSClientCompat.toLocalRenderSpaceOf(level, anchorPos, pos);
        }
        return pos;
    }
}
