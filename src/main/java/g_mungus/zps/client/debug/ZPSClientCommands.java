package g_mungus.zps.client.debug;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;

@EventBusSubscriber(modid = "zps", value = Dist.CLIENT)
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
