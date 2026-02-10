package g_mungus.zps.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class VSCompat {

    /// Only call after verifying that VS is loaded
    static Vec3 shipToWorld(ServerLevel level, BlockPos pos) {
        return VSGameUtilsKt.toWorldCoordinates(level, pos);
    }
}
