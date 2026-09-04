package g_mungus.zps.client;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.client.model.connected.ConnectedModelLoader;
import g_mungus.zps.client.model.connected.ConnectedTextureMeta;
import g_mungus.zps.client.recipebook.ModRecipeBookCategories;
import g_mungus.zps.client.renderer.*;
import g_mungus.zps.client.screens.AssemblerScreen;
import g_mungus.zps.client.screens.CoalBurnerScreen;
import g_mungus.zps.client.debug.GasPressureOverlay;
import g_mungus.zps.gas.ModParticles;
import g_mungus.zps.client.screens.PowerCellScreen;
import g_mungus.zps.client.screens.RollingMillScreen;
import g_mungus.zps.client.screens.SieveScreen;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.entity.ModEntities;
import g_mungus.zps.item.AddressPadClientHooks;
import g_mungus.zps.menu.ModMenus;
import g_mungus.zps.recipe.ModRecipeBookTypes;
import g_mungus.zps.recipe.ModRecipes;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import g_mungus.zps.commands.content.arguments.AssemblerRecipeArgument;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.valkyrienskies.kelvin.impl.client.particle.DefaultGasParticleProvider;

import java.util.List;
import java.util.function.Supplier;

public class ClientSetup {
    private static final ModelResourceLocation ADDRESS_PAD_BER_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "item/address_pad_ber"));
    private static final ModelResourceLocation ROBOTIC_ARM_SEGMENT_BER_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "item/robotic_arm_segment"));
    private static final ModelResourceLocation ROBOTIC_ARM_SWIVEL_BASE_BER_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "item/robotic_arm_swivel_base"));
    private static final ModelResourceLocation POWER_CELL_DIVIDER_BER_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "item/power_cell_divider"));
    private static final ModelResourceLocation POWER_DRILL_MODEL =
            new ModelResourceLocation(ZPSMod.resource("power_drill"), "inventory");
    private static final ModelResourceLocation CHAINSAW_MODEL =
            new ModelResourceLocation(ZPSMod.resource("chainsaw"), "inventory");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeybinds.register(event);
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ConnectedModelLoader.ID, ConnectedModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Connected-texture metadata is cached at bake time; drop it on reload so it is re-read.
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> ConnectedTextureMeta.clear());
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ADDRESS_PAD_BER_MODEL);
        event.register(ROBOTIC_ARM_SEGMENT_BER_MODEL);
        event.register(ROBOTIC_ARM_SWIVEL_BASE_BER_MODEL);
        event.register(POWER_CELL_DIVIDER_BER_MODEL);
        event.register(PoweredToolItemRenderer.BASE_MODEL);
        event.register(PoweredToolItemRenderer.HEAD_MODEL);
        event.register(ChainsawItemRenderer.BLADE_MODEL);
        event.register(RollingMillBlockEntityRenderer.ROLLER_MODEL);
        event.register(ImpactPistonBlockEntityRenderer.ROD_MODEL);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel powerDrillModel = event.getModels().get(POWER_DRILL_MODEL);
        if (powerDrillModel != null) {
            event.getModels().put(POWER_DRILL_MODEL, new CustomRendererItemModel(powerDrillModel));
        }
        BakedModel chainsawModel = event.getModels().get(CHAINSAW_MODEL);
        if (chainsawModel != null) {
            event.getModels().put(CHAINSAW_MODEL, new CustomRendererItemModel(chainsawModel));
        }
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        // Kelvin's own gas particle renderer, driven by our sprite set.
        event.registerSpriteSet(ModParticles.FLUX.get(), DefaultGasParticleProvider::new);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COAL_BURNER.get(), CoalBurnerScreen::new);
        event.register(ModMenus.POWER_CELL.get(), PowerCellScreen::new);
        event.register(ModMenus.ROLLING_MILL.get(), RollingMillScreen::new);
        event.register(ModMenus.ASSEMBLER.get(), AssemblerScreen::new);
        event.register(ModMenus.SIEVE.get(), SieveScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRecipeBookCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerAggregateCategory(ModRecipeBookCategories.ROLLING_MILL_SEARCH, List.of(ModRecipeBookCategories.ROLLING_MILL));
        event.registerBookCategories(ModRecipeBookTypes.ROLLING_MILL,
                List.of(ModRecipeBookCategories.ROLLING_MILL_SEARCH, ModRecipeBookCategories.ROLLING_MILL));
        event.registerRecipeCategoryFinder(ModRecipes.ROLLING_TYPE.get(), recipe -> ModRecipeBookCategories.ROLLING_MILL);
    }

    @SuppressWarnings({"deprecation", "removal"})
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Lets the (common-side) recipe argument reach the client's synced recipe manager for suggestions.
            AssemblerRecipeArgument.setClientLevelSupplier(() -> Minecraft.getInstance().level);
            EntityRenderers.register(ModEntities.OCTO_MOUNTING.get(), OctoMountingRenderer::new);
            EntityRenderers.register(ModEntities.DODECA_MOUNTING.get(), DodecaMountingRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.GRADUATED_LEVER.get(), GraduatedLeverBlockEntityRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.DATA_LECTERN.get(), DataLecternBlockEntityRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.SCRIPT_TERMINAL.get(), ScriptTerminalBlockEntityRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.TEXT_DISPLAY.get(), TextDisplayBlockEntityRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.RADIO_TRANSMITTER.get(), RadioTransmitterBlockEntityRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.RADIO_RECEIVER.get(), RadioReceiverBlockEntityRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.ROBOTIC_ARM.get(), RoboticArmBlockEntityRenderer::new);
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.ROBOTIC_ARM.get())
                    .factory(RoboticArmVisual::new)
                    // The BER keeps rendering the held item, debug overlays, and the on-ship fallback
                    .neverSkipVanillaRender()
                    .apply();
            BlockEntityRenderers.register(ModBlockEntities.ROLLING_MILL.get(), RollingMillBlockEntityRenderer::new);
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.ROLLING_MILL.get())
                    .factory(RollingMillVisual::new)
                    // The BER draws the rollers as a fallback when Flywheel's backend is unavailable.
                    .neverSkipVanillaRender()
                    .apply();
            BlockEntityRenderers.register(ModBlockEntities.IMPACT_PISTON.get(), ImpactPistonBlockEntityRenderer::new);
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.IMPACT_PISTON.get())
                    .factory(ImpactPistonVisual::new)
                    // The BER draws the rod as a fallback when Flywheel's backend is unavailable.
                    .neverSkipVanillaRender()
                    .apply();
            BlockEntityRenderers.register(ModBlockEntities.POWER_CELL.get(), PowerCellBlockEntityRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.DENSE_CABLE_SEPARATOR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.DATA_CABLE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SERIAL_BUS.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.RADIO_ANTENNA.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.DATA_TRANSCRIBER.get(), RenderType.cutout());

            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SPACE_SCAFFOLD.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SPACE_TRUSS.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SPACE_GRATING_BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CATWALK_STAIRS.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CATWALK.get(), RenderType.cutout());

            NeoForge.EVENT_BUS.addListener(AddressPadClientHooks::onRenderLevelStage);
            NeoForge.EVENT_BUS.addListener(GasPressureOverlay::onRenderLevelStage);
            NeoForge.EVENT_BUS.addListener(GasPressureOverlay::onPlayerTick);
        });
    }

    @SubscribeEvent
    public static void loadCompleted(FMLLoadCompleteEvent event) {

        ModContainer modContainer = ModList.get()
                .getModContainerById(ZPSMod.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("ZPS Mod Container missing after loadCompleted"));

        Supplier<IConfigScreenFactory> configScreen = () ->
                (mc, previousScreen) -> new BaseConfigScreen(previousScreen, ZPSMod.MOD_ID);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, configScreen);

        BaseConfigScreen.setDefaultActionFor(ZPSMod.MOD_ID, base -> base
                .withButtonLabels("Client Settings", null, "Server Settings")
                .withSpecs(ZPSConfig.CONFIG_SPEC, null, ZPSConfig.SERVER_CONFIG_SPEC)
        );
    }
} 
