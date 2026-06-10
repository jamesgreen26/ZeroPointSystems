package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public class ClientCompat {

    /// Maps a shipyard position to its world-space render position. Returns the input unchanged
    /// when VS is absent or the position is not managed by a ship.
    public static Vec3 toWorldRenderPos(ClientLevel level, Vec3 pos) {
        if (Compat.isVSLoaded()) {
            return VSClientCompat.toWorldRenderPos(level, pos);
        }
        return pos;
    }
}
