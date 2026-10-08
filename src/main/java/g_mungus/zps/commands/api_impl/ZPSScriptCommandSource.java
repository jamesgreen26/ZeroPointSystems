package g_mungus.zps.commands.api_impl;

import g_mungus.munguscript.engine.host.RunState;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The command source a script command runs with: whoever sent it, the block it is aimed at, and
 * the engine's state for the run. It rides on a {@code CommandSourceStack}, which is all Brigadier
 * passes through a command.
 */
public record ZPSScriptCommandSource(@Nullable CommandSource delegate, BlockPos target,
                                     @Nullable RunState runState) implements CommandSource {

    public ZPSScriptCommandSource withRunState(RunState state) {
        return new ZPSScriptCommandSource(delegate, target, state);
    }

    @Override
    public void sendSystemMessage(@NotNull Component message) {
        if (delegate != null) {
            delegate.sendSystemMessage(message);
        }
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }
}
