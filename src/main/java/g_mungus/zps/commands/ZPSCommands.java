package g_mungus.zps.commands;


import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber
public class ZPSCommands {

    @SubscribeEvent
    public static void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();

        dispatcher.register(
                Commands.literal("zps_script")
                        .requires(src -> src.hasPermission(2))
                        .then(
                                Commands.argument("positions", BlockPosListArgument.blockPosList())
                                        .then(
                                                Commands.argument("filter", BlockPredicateArgument.blockPredicate(buildContext))
                                                        .executes(ctx -> {
                                                            List<BlockPos> positions =
                                                                    BlockPosListArgument.getBlockPosList(ctx, "positions");

                                                            ctx.getSource().getPlayerOrException()
                                                                    .sendSystemMessage(
                                                                            Component.literal("Got " + positions.size() + " positions")
                                                                    );

                                                            return 1;
                                                        })
                                        )
                        )
        );

    }
}
