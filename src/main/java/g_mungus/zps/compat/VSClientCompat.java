package g_mungus.zps.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class VSClientCompat {

    /// Only call after verifying that VS is loaded
    static Vec3 toWorldRenderPos(ClientLevel level, Vec3 pos) {
        ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos.x, pos.y, pos.z);
        if (ship == null) return pos;
        Vector3d worldPos = ship.getRenderTransform().getShipToWorld()
                .transformPosition(new Vector3d(pos.x, pos.y, pos.z));
        return new Vec3(worldPos.x, worldPos.y, worldPos.z);
    }
}
