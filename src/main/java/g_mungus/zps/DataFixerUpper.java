package g_mungus.zps;

import g_mungus.zps.compat.Compat;
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

    /** Translates a legacy registry path to its current path (handles in-place renames). */
    private static String remapPath(String oldPath) {
        if (oldPath.equals("light_pipe_cable")) {
            return "data_cable";
        }
        return oldPath;
    }

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {

        // ---- BLOCKS ----
        // Remap both the legacy ZPL namespace and stale ZPS ids left over from in-place renames.
        for (String namespace : new String[]{Compat.ZPL_MOD_ID, ZPSMod.MOD_ID}) {
            event.getMappings(ForgeRegistries.Keys.BLOCKS, namespace)
                    .forEach(mapping -> {
                        ResourceLocation oldId = mapping.getKey();
                        ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(
                                ZPSMod.MOD_ID,
                                remapPath(oldId.getPath())
                        );

                        Block newBlock = ForgeRegistries.BLOCKS.getValue(newId);

                        if (newBlock != null) {
                            mapping.remap(newBlock);
                        }
                    });
        }

        // ---- ITEMS ----
        for (String namespace : new String[]{Compat.ZPL_MOD_ID, ZPSMod.MOD_ID}) {
            event.getMappings(ForgeRegistries.Keys.ITEMS, namespace)
                    .forEach(mapping -> {
                        ResourceLocation oldId = mapping.getKey();
                        ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(
                                ZPSMod.MOD_ID,
                                remapPath(oldId.getPath())
                        );

                        Item newItem = ForgeRegistries.ITEMS.getValue(newId);
                        if (newItem != null) {
                            mapping.remap(newItem);
                        }
                    });
        }
    }
}
