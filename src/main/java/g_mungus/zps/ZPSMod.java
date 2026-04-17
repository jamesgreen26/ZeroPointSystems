package g_mungus.zps;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.client.ponder.ZPSPonderPlugin;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.entity.ModEntities;
import g_mungus.zps.gametest.EnumPropertyWithAliasesGameTests;
import g_mungus.zps.item.ModCreativeTabs;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.gametest.CableNetworkGameTests;
import g_mungus.zps.gametest.TextDisplayGameTests;
import g_mungus.zps.networking.ZPSGamePackets;
import g_mungus.zps.painting.ZPSPaintings;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ZPSMod.MOD_ID)
public final class ZPSMod {
    public static final String MOD_ID = "zps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ZPSMod(IEventBus modEventBus, Dist dist, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ZPSConfig.CONFIG_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ZPSConfig.SERVER_CONFIG_SPEC);
        DataFixerUpper.registerAliases();

        // Register blocks and block entities
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ZPSPaintings.PAINTING_VARIANTS.register(modEventBus);

        modEventBus.addListener(ZPSGamePackets::register);
        modEventBus.addListener(ZPSMod::registerGameTests);

        if (dist == Dist.CLIENT) {
            ZPSPonderPlugin.registerPlugin();
        }

        Compat.onModInit(modEventBus);
    }

    public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(TextDisplayGameTests.class);
        event.register(CableNetworkGameTests.class);
        event.register(EnumPropertyWithAliasesGameTests.class);
    }
}
