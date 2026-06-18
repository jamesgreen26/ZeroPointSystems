package g_mungus.zps.commands.api_impl;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class TypeKeys {
    public static final Map<ResourceLocation, Class<?>> TYPE_KEY_TO_CLASS = Map.of(
            ResourceLocation.parse("zps:int"),       Integer.class,
            ResourceLocation.parse("zps:double"),    Double.class,
            ResourceLocation.parse("zps:string"),    String.class,
            ResourceLocation.parse("zps:boolean"),   Boolean.class,
            ResourceLocation.parse("zps:block_pos"), BlockPos.class,
            ResourceLocation.parse("zps:item"),      ItemStack.class,
            ResourceLocation.parse("zps:vec_pos"),   Vec3.class,
            ResourceLocation.parse("zps:vec_dir"),   Vec3.class,
            ResourceLocation.parse("zps:vec_box"),   Vec3.class
    );
}
