package g_mungus.zps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

@Mod.EventBusSubscriber(
        modid = ZPSMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class DataFixerUpper {

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {

        // ---- BLOCKS ----
        event.getMappings(ForgeRegistries.Keys.BLOCKS, Compat.ZPL_MOD_ID)
                .forEach(mapping -> {
                    ResourceLocation oldId = mapping.getKey();
                    ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(
                            ZPSMod.MOD_ID,
                            oldId.getPath()
                    );

                    Block newBlock = ForgeRegistries.BLOCKS.getValue(newId);

                    if (newBlock != null) {
                        mapping.remap(newBlock);
                    }
                });

        // ---- ITEMS ----
        event.getMappings(ForgeRegistries.Keys.ITEMS, Compat.ZPL_MOD_ID)
                .forEach(mapping -> {
                    ResourceLocation oldId = mapping.getKey();
                    ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(
                            ZPSMod.MOD_ID,
                            oldId.getPath()
                    );

                    Item newItem = ForgeRegistries.ITEMS.getValue(newId);
                    if (newItem != null) {
                        mapping.remap(newItem);
                    }
                });
    }
}
