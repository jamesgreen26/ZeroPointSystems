package g_mungus.zps.commands.content;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptExecutor;
import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
    }
}
