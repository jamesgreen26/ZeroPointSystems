package g_mungus.zps.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ZPSCommands {

    public static final String PREFIX = "zps_script";

    @SubscribeEvent
    public static void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        zpsScript(dispatcher, SetRedstoneCommand.COMMAND);
    }

    private static void zpsScript(CommandDispatcher<CommandSourceStack> dispatcher, CommandNode<CommandSourceStack> command) {
        dispatcher.register(
                Commands.literal(PREFIX)
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .then(command).build()));
    }
}
