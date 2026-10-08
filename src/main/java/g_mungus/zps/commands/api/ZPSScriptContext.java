package g_mungus.zps.commands.api;

import g_mungus.munguscript.language.node.ScriptContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where a script command runs: the block it is aimed at, and the command source running it. Every
 * ZPS node function gets one, through {@link #of}.
 *
 * @param pos           the block the command is aimed at, the one a Serial Bus faces
 * @param commandSource what relative coordinates and the like are resolved against
 */
public record ZPSScriptContext(BlockPos pos, ServerLevel level, CommandSourceStack commandSource) {

    /** The ZPS context of a run, as the engine hands it to a node. */
    public static ZPSScriptContext of(ScriptContext context) {
        return context.host(ZPSScriptContext.class);
    }
}
