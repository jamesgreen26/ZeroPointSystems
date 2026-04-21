package g_mungus.zps.lidar;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface RayCast {
    double invoke(Level level, Vec3 start, Vec3 dir, double length);
}
