package g_mungus.zps.commands.content;

import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptContext;
import g_mungus.zps.commands.api.ScriptGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ZPSScriptGetters {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(new ScriptGetter<>(
                "pos",
                BlockPos.class,
                ResourceLocation.parse("zps:block_pos"),
                ScriptContext::pos
        ));

        event.register(new ScriptGetter<>(
                "dimension",
                String.class,
                ResourceLocation.parse("zps:dimension"),
                scriptContext -> scriptContext.level().dimension().location().toString()
        ));
    }
}
