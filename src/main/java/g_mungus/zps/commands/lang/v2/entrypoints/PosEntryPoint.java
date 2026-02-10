package g_mungus.zps.commands.lang.v2.entrypoints;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import g_mungus.zps.commands.ZPSScriptCommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;

public class PosEntryPoint {
    public static LiteralArgumentBuilder<CommandSourceStack> getCommand(CommandDispatcher<CommandSourceStack> dispatcher) {

        return Commands.literal("POS")
                .forward(dispatcher.getRoot(), context -> {

                    return List.of(context.getSource().withSource(new ZPSScriptCommandSource(context.getSource().source)));
                }, false);
    }
}
