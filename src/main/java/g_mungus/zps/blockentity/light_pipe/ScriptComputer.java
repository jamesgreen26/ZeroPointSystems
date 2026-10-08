package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.networking.ScriptComputerC2SPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public interface ScriptComputer {
    void acceptUpdatePacket(ScriptComputerC2SPacket packet);

    BlockPos getPos();

    boolean canEdit(Vec3 eyePosition);

    String getValue();

    boolean getLoop();

    int getDelay();

    /** The addresses scripts here can write as {@code @name}, by name. */
    default Map<String, BlockPos> getAddresses() {
        return Map.of();
    }
}
