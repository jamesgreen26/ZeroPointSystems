package g_mungus;

import g_mungus.block.ModBlocks;
import g_mungus.blockentity.ModBlockEntities;
import g_mungus.entity.ModEntities;
import g_mungus.item.ModCreativeTabs;
import g_mungus.item.ModItems;

import g_mungus.networking.ZPSGamePackets;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ZPSMod.MOD_ID)
public final class ZPSMod {
    public static final String MOD_ID = "zps";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ZPSMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // Register blocks and block entities
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        // Register common setup event
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ZPSGamePackets.register();
    }
}
