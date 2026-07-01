package g_mungus.zps;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.item.ModItems;
import net.minecraft.resources.ResourceLocation;

public final class DataFixerUpper {
    private DataFixerUpper() {}

    public static void registerAliases() {
        ModBlocks.BLOCKS.getEntries().forEach(holder -> {
            ResourceLocation oldId = ResourceLocation.fromNamespaceAndPath(Compat.ZPL_MOD_ID, holder.getId().getPath());
            ModBlocks.BLOCKS.addAlias(oldId, holder.getId());
        });

        ModItems.ITEMS.getEntries().forEach(holder -> {
            ResourceLocation oldId = ResourceLocation.fromNamespaceAndPath(Compat.ZPL_MOD_ID, holder.getId().getPath());
            ModItems.ITEMS.addAlias(oldId, holder.getId());
        });

        ModBlocks.BLOCKS.addAlias(ZPSMod.resource("light_pipe_cable"), ZPSMod.resource("data_cable"));
        ModItems.ITEMS.addAlias(ZPSMod.resource("light_pipe_cable"), ZPSMod.resource("data_cable"));
    }
}
