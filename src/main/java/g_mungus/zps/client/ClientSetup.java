package g_mungus.zps.client;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.client.model.connected.ConnectedModelLoader;
import g_mungus.zps.client.model.connected.ConnectedTextureMeta;
import g_mungus.zps.client.renderer.*;
import g_mungus.zps.client.screens.CoalBurnerScreen;
import g_mungus.zps.client.screens.PowerCellScreen;
import g_mungus.zps.entity.ModEntities;
import g_mungus.zps.item.AddressPadClientHooks;
import g_mungus.zps.menu.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ZPSMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {
    private static final ModelResourceLocation ADDRESS_PAD_BER_MODEL =
            new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "address_pad_ber"), "inventory");
    private static final ModelResourceLocation ROBOTIC_ARM_SEGMENT_BER_MODEL =
            new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "robotic_arm_segment"), "inventory");
    private static final ModelResourceLocation ROBOTIC_ARM_SWIVEL_BASE_BER_MODEL =
            new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "robotic_arm_swivel_base"), "inventory");
    private static final ModelResourceLocation POWER_CELL_DIVIDER_BER_MODEL =
            new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "power_cell_divider"), "inventory");
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
        event.register(ConnectedModelLoader.NAME, ConnectedModelLoader.INSTANCE);
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

    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.OCTO_MOUNTING.get(), OctoMountingRenderer::new);
            EntityRenderers.register(ModEntities.DODECA_MOUNTING.get(), DodecaMountingRenderer::new);
            MenuScreens.register(ModMenus.COAL_BURNER.get(), CoalBurnerScreen::new);
            MenuScreens.register(ModMenus.POWER_CELL.get(), PowerCellScreen::new);
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

            MinecraftForge.EVENT_BUS.addListener(AddressPadClientHooks::onRenderLevelStage);
        });
    }
} 
