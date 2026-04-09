package g_mungus.zps.commands.content;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.content.arguments.RadioFrequencyArgument;
import g_mungus.zps.commands.content.executors.SetFrequencyCommand;
import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import g_mungus.zps.commands.content.executors.SetPageCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber
public class ZPSScriptExecutors {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(ScriptExecutor.simple(
                "set_redstone",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                IntegerArgumentType.integer(0, 15),
                (power, context) -> {
                    SetRedstoneCommand.setRedstone(
                            context.commandSource().getLevel(),
                            context.pos(),
                            power
                    );
                    return 1;
                }
        ));

        event.register(ScriptExecutor.simpleWithBlocks(
                "set_page",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                IntegerArgumentType.integer(1, 100),
                (page, context) -> SetPageCommand.setPage(
                        context.commandSource().getLevel(),
                        context.pos(),
                        page
                ),
                Set.of(ZPSMod.resource("data_lectern"), ResourceLocation.withDefaultNamespace("lectern"))
        ));

        event.register(ScriptExecutor.simpleWithBlocks(
                "write_page",
                String.class,
                ResourceLocation.parse("zps:string"),
                StringArgumentType.string(),
                (text, context) -> SetPageCommand.writeToCurrentPage(
                        context.commandSource().getLevel(),
                        context.pos(),
                        text
                ),
                Set.of(ZPSMod.resource("data_lectern"), ResourceLocation.withDefaultNamespace("lectern"))
        ));

        event.register(ScriptExecutor.simpleWithBlocks(
                "set_frequency",
                Integer.class,
                ResourceLocation.parse("zps:int"),
                RadioFrequencyArgument.radioFrequency(),
                (frequencyIndex, context) -> SetFrequencyCommand.setFrequency(
                        context.commandSource().getLevel(),
                        context.pos(),
                        frequencyIndex
                ),
                Set.of(ZPSMod.resource("radio_transmitter"), ZPSMod.resource("radio_receiver"))
        ));
    }
}
