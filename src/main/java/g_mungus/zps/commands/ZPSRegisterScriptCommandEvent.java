package g_mungus.zps.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.eventbus.api.Event;

public class ZPSRegisterScriptCommandEvent extends Event {
    private final ArgumentBuilder<CommandSourceStack, ?> builder;

    ZPSRegisterScriptCommandEvent(ArgumentBuilder<CommandSourceStack, ?> builder) {
        this.builder = builder;
    }

    public void addCommand(CommandNode<CommandSourceStack> argument) {
        builder.then(argument);
    }

    public void addCommand(ArgumentBuilder<CommandSourceStack, ?> argument) {
        addCommand(argument.build());
    }
}
