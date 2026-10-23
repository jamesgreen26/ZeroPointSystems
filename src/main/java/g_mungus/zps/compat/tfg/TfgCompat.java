package g_mungus.zps.compat.tfg;

import g_mungus.zps.ZPSMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

public class TfgCompat {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(TfgCompat::addRecipePack);
    }

    /** Built-in pack whose recipes replace the ZPS ones of the same name. */
    private static void addRecipePack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        Path path = ModList.get().getModFileById(ZPSMod.MOD_ID).getFile().findResource("datapacks/tfg_compat");
        Pack pack = Pack.readMetaAndCreate(
                ZPSMod.MOD_ID + ":tfg_compat",
                Component.literal("ZPS TFG Compat"),
                true,
                id -> new PathPackResources(id, path, true),
                PackType.SERVER_DATA,
                Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
