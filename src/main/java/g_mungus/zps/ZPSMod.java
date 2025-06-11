package g_mungus.zps;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.entity.ModEntities;
import g_mungus.zps.item.ModCreativeTabs;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.networking.ZPSGamePackets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ZPSMod.MOD_ID)
public final class ZPSMod {
    public static final String MOD_ID = "zps";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ZPSMod(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.register(modBus);
        ModEntities.ENTITIES.register(modBus);

        modBus.addListener(ZPSGamePackets::onRegisterPayloadHandler);
    }
}
