package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.commands.api.ZPSScriptContext;
import net.minecraft.core.BlockPos;

public class ReturnCommand {

    /**
     * Has the Serial Bus running the command answer with {@code value} in the blank tick that
     * follows, for a Script Terminal's {@code receive}. The bus is the command's source, so a
     * command run from anywhere else has nothing to answer from.
     */
    public static int answer(ZPSScriptContext context, String value) {
        BlockPos terminal = BlockPos.containing(context.commandSource().getPosition());
        if (context.level().getBlockEntity(terminal) instanceof SerialBusBlockEntity bus) {
            bus.setPendingAnswer(value);
            return 1;
        }
        return 0;
    }
}
