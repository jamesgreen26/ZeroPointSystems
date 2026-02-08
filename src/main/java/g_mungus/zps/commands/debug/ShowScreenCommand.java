package g_mungus.zps.commands.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import g_mungus.zps.client.screens.ScriptComputerEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ShowScreenCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> COMMAND = Commands
            .literal("zps_debug").then(
                    Commands.literal("SHOW_SCREEN").then(Commands.literal("script_computer").executes(context -> {
                        Minecraft.getInstance().setScreen(new ScriptComputerEditScreen());
                        return 1;
                    }))
            );
}
