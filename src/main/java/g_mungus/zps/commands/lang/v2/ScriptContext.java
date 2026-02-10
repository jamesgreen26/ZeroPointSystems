package g_mungus.zps.commands.lang.v2;

import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class ScriptContext {
    public BlockPos getBlockPos() {
        throw new RuntimeException("not yet implemented");
    }

    public Vec3 getVecPos() {
        return Vec3.atLowerCornerOf(Compat.toWorldPos(getServerLevel(), getBlockPos()));
    }

    public ServerLevel getServerLevel() {
        throw new RuntimeException("not yet implemented");
    }
}
