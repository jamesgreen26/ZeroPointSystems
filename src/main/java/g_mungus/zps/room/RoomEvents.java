package g_mungus.zps.room;

import g_mungus.zps.ZPSMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Checks rooms whose blocks changed this tick, once each, after everything that could change them
 * has run. Block changes are reported by {@code ServerLevelMixin}; loaded chunks are searched for
 * flames.
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

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            RoomManager.get(level).onChunkLoaded(chunk);
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RoomManager.get(level).onChunkUnloaded(event.getChunk().getPos());
        }
    }
}
