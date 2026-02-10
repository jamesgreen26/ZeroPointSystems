package g_mungus.zps.commands.content;

import g_mungus.zps.commands.api.RegisterScriptCommandsEvent;
import g_mungus.zps.commands.api.ScriptMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ZPSScriptMappers {

    @SubscribeEvent
    public static void onRegisterEvent(RegisterScriptCommandsEvent event) {
        event.register(new ScriptMapper<>(
                "x",
                BlockPos.class,
                int.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:int"),
                (blockPos, scriptContext) -> blockPos.getX()
        ));

        event.register(new ScriptMapper<>(
                "y",
                BlockPos.class,
                int.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:int"),
                (blockPos, scriptContext) -> blockPos.getY()
        ));

        event.register(new ScriptMapper<>(
                "z",
                BlockPos.class,
                int.class,
                ResourceLocation.parse("zps:block_pos"),
                ResourceLocation.parse("zps:int"),
                (blockPos, scriptContext) -> blockPos.getZ()
        ));
    }
}
