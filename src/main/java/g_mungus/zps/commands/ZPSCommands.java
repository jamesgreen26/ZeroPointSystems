package g_mungus.zps.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ZPSCommands {

    @SubscribeEvent
    public static void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        ArgumentBuilder<CommandSourceStack, RequiredArgumentBuilder<CommandSourceStack, BlockPredicateArgument.Result>> ROOT = Commands.argument("filter", BlockPredicateArgument.blockPredicate(buildContext));

        MinecraftForge.EVENT_BUS.post(new ZPSRegisterScriptCommandEvent(ROOT));

        dispatcher.register(
                Commands.literal("zps_script")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("positions", BlockPosListArgument.blockPosList())
                        .then(ROOT)));
    }

    @SubscribeEvent
    public static void onRegisterZPSScriptCommands(ZPSRegisterScriptCommandEvent event) {
        event.addCommand(Commands.literal("TEST_0"));
        event.addCommand(Commands.literal("TEST_1"));
    }
}
