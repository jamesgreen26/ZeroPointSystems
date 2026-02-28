package g_mungus.zps.client.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import g_mungus.zps.client.screens.ScriptTerminalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ShowScreenCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND = Commands
            .literal("zps_debug").then(
                    Commands.literal("show_screen").then(Commands.literal("script_computer").executes(context -> {
                        Minecraft.getInstance().setScreen(new ScriptTerminalScreen(null, true));
                        return 1;
                    }))
            );
}
