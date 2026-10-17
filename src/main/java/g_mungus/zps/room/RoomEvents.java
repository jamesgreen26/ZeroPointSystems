package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Checks rooms whose blocks changed this tick, once each, after everything that could change them
 * has run. Block changes are reported by {@code ServerLevelMixin}.
 */
@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class RoomEvents {

    private RoomEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RoomManager.get(level).tick(level);
        }
    }
}
