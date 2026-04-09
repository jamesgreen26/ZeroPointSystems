package g_mungus.zps.commands.api_impl;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class TypeKeys {
    public static final Map<ResourceLocation, Class<?>> TYPE_KEY_TO_CLASS = Map.of(
            ResourceLocation.parse("zps:int"),       Integer.class,
            ResourceLocation.parse("zps:double"),    Double.class,
            ResourceLocation.parse("zps:string"),    String.class,
            ResourceLocation.parse("zps:boolean"),   Boolean.class,
            ResourceLocation.parse("zps:block_pos"), BlockPos.class
    );
}
