package g_mungus.zps.client;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.client.renderer.OctoMountingRenderer;
import g_mungus.zps.client.renderer.ScriptTransmitterBlockEntityRenderer;
import g_mungus.zps.entity.ModEntities;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ZPSMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeybinds.register(event);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            EntityRenderers.register(ModEntities.OCTO_MOUNTING.get(), OctoMountingRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.SCRIPT_TRANSMITTER.get(), ScriptTransmitterBlockEntityRenderer::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.DENSE_CABLE_SEPARATOR.get(), RenderType.cutout());
        });
    }
} 