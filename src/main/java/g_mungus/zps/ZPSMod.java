package g_mungus.zps;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.client.ponder.ZPSPonderPlugin;
import g_mungus.zps.client.renderer.ZPSPartialModels;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.entity.ModEntities;
import g_mungus.zps.gametest.EnumPropertyWithAliasesGameTests;
import g_mungus.zps.gametest.RoboticArmGameTests;
import g_mungus.zps.gametest.RollingMillGameTests;
import g_mungus.zps.gametest.AssemblerGameTests;
import g_mungus.zps.gametest.ImpactPistonGameTests;
import g_mungus.zps.item.ModCreativeTabs;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.gametest.CableNetworkGameTests;
import g_mungus.zps.gametest.TextDisplayGameTests;
import g_mungus.zps.menu.ModMenus;
import g_mungus.zps.networking.ZPSGamePackets;
import g_mungus.zps.recipe.ModRecipeBookTypes;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.painting.ZPSPaintings;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ZPSMod.MOD_ID)
public final class ZPSMod {
    public static final String MOD_ID = "zps";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ZPSMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.CLIENT, ZPSConfig.CONFIG_SPEC);
        context.registerConfig(ModConfig.Type.SERVER, ZPSConfig.SERVER_CONFIG_SPEC);

        // Register blocks and block entities
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ZPSPaintings.PAINTING_VARIANTS.register(modEventBus);

        // Register common setup event
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ZPSMod::registerGameTests);
        // Registry aliases for in-place renames (must run during registration, before registries lock).
        modEventBus.addListener(DataFixerUpper::registerRenameAliases);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ZPSPonderPlugin::registerPlugin);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ZPSPartialModels::init);

        // Force-initialise the extended vanilla RecipeBookType enum values during mod construction, so the
        // IExtensibleEnum#create hooks run before the recipe book is used.
        //noinspection ResultOfMethodCallIgnored
        ModRecipeBookTypes.ROLLING_MILL.getClass();

        Compat.onModInit(modEventBus);
    }

    public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ZPSGamePackets.register();
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        event.register(TextDisplayGameTests.class);
        event.register(CableNetworkGameTests.class);
        event.register(EnumPropertyWithAliasesGameTests.class);
        event.register(RoboticArmGameTests.class);
        event.register(RollingMillGameTests.class);
        event.register(AssemblerGameTests.class);
        event.register(ImpactPistonGameTests.class);
    }
}
