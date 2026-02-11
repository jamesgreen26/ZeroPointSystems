package g_mungus.zps.commands.api;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface ScriptContext {
    BlockPos pos();
    ServerLevel level();
    CommandSourceStack commandSource();

    interface WithArgument<A> extends ScriptContext {
        A argumentValue();
    }
}
