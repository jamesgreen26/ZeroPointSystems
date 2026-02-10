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
                BlockPos.class,
                ResourceLocation.parse("block_pos:pos"),
                ResourceLocation.parse("block_pos:pos"),
                ScriptContext::pos
        ));

        event.register(new ScriptGetter<>(
                String.class,
                ResourceLocation.parse("string:dimension"),
                ResourceLocation.parse("string:dimension"),
                scriptContext -> scriptContext.level().dimension().location().toString()
        ));
    }
}
