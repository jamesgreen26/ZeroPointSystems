package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class LoadScriptCommand {

    /** Loads {@code text} into the Script Terminal at {@code pos} as if from a file; see {@link ScriptTerminalBlockEntity#loadScript}. */
    public static int load(ServerLevel level, BlockPos pos, String text) {
        if (level.getBlockEntity(pos) instanceof ScriptTerminalBlockEntity terminal && terminal.loadScript(text)) {
            return 1;
        }
        return 0;
    }
}
