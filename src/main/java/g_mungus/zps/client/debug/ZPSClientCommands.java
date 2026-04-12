package g_mungus.zps.client.debug;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod.EventBusSubscriber(
        modid = "zps",
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class ZPSClientCommands {

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        if (!FMLLoader.isProduction()) {
            dispatcher.register(ShowScreenCommand.COMMAND);
            dispatcher.register(TtsDebugCommand.COMMAND);
            dispatcher.register(ExportBookCharWidthsCommand.COMMAND);
        }
    }
}
