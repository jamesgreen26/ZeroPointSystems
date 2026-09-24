package g_mungus.zps.reactor;

import g_mungus.zps.ZPSMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the reactors. The tick runs before block entities and before Kelvin's own solve, so
 * every exchanger and port sees the same chamber state the reactor judged this tick.
 */
@Mod.EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class ReactorEvents {

    private ReactorEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (event.level instanceof ServerLevel level) {
            ReactorManager.get(level).tick(level);
        }
    }

    /**
     * Wall blocks report their own placement and removal; this catches everything else that can
     * change a tracked position — commands, explosions, a block appearing inside the cavity.
     */
    @SubscribeEvent
    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ReactorManager manager = ReactorManager.get(level);
        if (manager.isTracked(event.getPos())) {
            manager.onTrackedPositionChanged(level, event.getPos());
        }
    }
}
