package g_mungus.zps.commands.lang.v2.entrypoints;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import g_mungus.zps.commands.ZPSCommands;
import g_mungus.zps.commands.lang.v2.classes.BlockPosClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;

public class PosEntryPoint {
    public static LiteralArgumentBuilder<CommandSourceStack> getCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandDispatcher<CommandSourceStack> newDispatcher = new CommandDispatcher<>();

        return Commands.literal("POS")
                .forward(newDispatcher.getRoot(), context -> {
                    BlockPosClass clazz = new BlockPosClass("POS", ZPSCommands.getPosition(context));
                    clazz.getFunctions().forEach(it -> {
                        newDispatcher.register(Commands.literal(it.name()));
                    });
                    clazz.getComparators().forEach(it -> {
                        newDispatcher.register(Commands.literal(it.getName()));
                    });
                    return List.of(context.getSource());
                }, false);
    }
}
