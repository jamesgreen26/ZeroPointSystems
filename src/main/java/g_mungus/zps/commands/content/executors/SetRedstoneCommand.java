package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.commands.api.ZPSScriptContext;
import net.minecraft.core.BlockPos;

public class SetRedstoneCommand {

    /**
     * Has the Serial Bus running the command emit {@code power} out of its front face. The bus is
     * the command's source, so a command run from anywhere else has nothing to emit from.
     */
    public static int setRedstone(ZPSScriptContext context, int power) {
        BlockPos terminal = BlockPos.containing(context.commandSource().getPosition());
        if (context.level().getBlockEntity(terminal) instanceof SerialBusBlockEntity bus) {
            bus.setRedstoneOutput(power);
            return 1;
        }
        return 0;
    }
}
