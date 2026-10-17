package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

/**
 * Checks rooms whose blocks changed this tick, once each, after everything that could change them
 * has run. Block changes are reported by {@code ServerLevelMixin}.
 */
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class RoomEvents {

    private RoomEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level instanceof ServerLevel level) {
            RoomManager.get(level).tick(level);
        }
    }
}
