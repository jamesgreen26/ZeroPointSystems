package g_mungus.zps.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ClientCompat {

    public static boolean isOnShip(Level level, BlockPos pos) {
        return false;
    }

    public static Vec3 toWorldRenderPos(Level level, Vec3 pos) {
        return pos;
    }

    public static Vec3 toLocalRenderSpaceOf(Level level, BlockPos anchorPos, Vec3 pos) {
        return pos;
    }
}
