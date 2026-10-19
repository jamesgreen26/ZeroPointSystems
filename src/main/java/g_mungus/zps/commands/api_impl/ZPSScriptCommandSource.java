package g_mungus.zps.commands.api_impl;

import g_mungus.munguscript.engine.host.RunState;
import g_mungus.zps.commands.api.PhysStep;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The command source a script command runs with: whoever sent it, the block it is aimed at, the
 * engine's state for the run, and the physics step it runs in, if it is a phys tick program's. It
 * rides on a {@code CommandSourceStack}, which is all Brigadier passes through a command.
 */
public record ZPSScriptCommandSource(@Nullable CommandSource delegate, BlockPos target,
                                     @Nullable RunState runState, @Nullable PhysStep physStep)
        implements CommandSource {

    public ZPSScriptCommandSource withRunState(RunState state) {
        return new ZPSScriptCommandSource(delegate, target, state, physStep);
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
