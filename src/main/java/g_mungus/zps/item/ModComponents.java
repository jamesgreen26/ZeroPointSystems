package g_mungus.zps.item;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ZPSMod.MOD_ID);

    /**
     * Marks an Assembler ghost/pattern display item as standing in for an item tag, carrying the tag ids the
     * cell accepts. Rides the normal menu item sync so the screen can cycle the preview and show a tag
     * tooltip (JEI-style). Empty/absent means the cell is a concrete item.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> GHOST_INGREDIENT_TAGS =
            COMPONENTS.register("ghost_ingredient_tags", () -> DataComponentType.<List<ResourceLocation>>builder()
                    .persistent(ResourceLocation.CODEC.listOf())
                    .networkSynchronized(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());
}
