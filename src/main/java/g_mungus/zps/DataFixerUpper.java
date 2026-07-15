package g_mungus.zps;

import g_mungus.zps.compat.Compat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(
        modid = ZPSMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class DataFixerUpper {

    /**
     * Registers registry aliases for blocks/items that were renamed in place (only {@code light_pipe_cable
     * -> data_cable} so far). Aliases resolve the old id at every registry lookup — including when a save's
     * block/item ids are loaded — so the old id resolves to the new value directly rather than reporting as
     * a missing mapping. This is the Forge 1.20.1 equivalent of the 1.21 branch's
     * {@code DeferredRegister#addAlias}. Fired during registration via {@link RegisterEvent} on the mod bus
     * (wired in {@code ZPSMod}); {@code ForgeRegistry#addAlias} throws once the registry is locked, so this
     * must run during registration and no later.
     */
    public static void registerRenameAliases(RegisterEvent event) {
        boolean blocks = event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS);
        boolean items = event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS);
        if (!blocks && !items) {
            return;
        }
        // ForgeRegistry#addAlias is the only entry point for registry aliases on Forge 1.20.1 (there is no
        // IForgeRegistry-level API), hence the cast.
        IForgeRegistry<?> registry = event.getForgeRegistry();
        if (registry instanceof ForgeRegistry<?> forgeRegistry) {
            forgeRegistry.addAlias(ZPSMod.resource("light_pipe_cable"), ZPSMod.resource("data_cable"));
        }
    }

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {

        // ---- BLOCKS ----
        // Migrate the legacy ZPL mod-id namespace to ZPS (same path).
        event.getMappings(ForgeRegistries.Keys.BLOCKS, Compat.ZPL_MOD_ID)
                .forEach(mapping -> {
                    ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, mapping.getKey().getPath());
                    Block newBlock = ForgeRegistries.BLOCKS.getValue(newId);
                    if (newBlock != null) {
                        mapping.remap(newBlock);
                    }
                });

        // ---- ITEMS ----
        event.getMappings(ForgeRegistries.Keys.ITEMS, Compat.ZPL_MOD_ID)
                .forEach(mapping -> {
                    ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, mapping.getKey().getPath());
                    Item newItem = ForgeRegistries.ITEMS.getValue(newId);
                    if (newItem != null) {
                        mapping.remap(newItem);
                    }
                });
    }
}
