package g_mungus.zps.tractor;

import g_mungus.zps.ZPSMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A player in a Tractor Beam is weightless, by the game's own no-gravity flag rather than by the upward push the
 * beam gives everything else.
 * <p>
 * Everything else is moved by the server, which can cancel gravity in the same breath as it sets the velocity. A
 * player is moved by its own client, a tick or more of latency away from the server's idea of where it is, and a
 * push timed on one side against a fall computed on the other shows up as a bob. So the player asks for itself,
 * every tick and on both sides, whether a beam has hold of it, and switches its own gravity to match. The server's
 * answer is also what other clients see.
 * <p>
 * Gravity is only ever given back to a player it was taken from here, and the record of that rides along in the
 * player's saved data, so a player who logs out mid-beam does not come back floating, and one who was weightless
 * for some other reason is left that way.
 */
@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public final class PlayerWeightlessness {
    private static final String TAKEN = "zps:TractorBeamWeightless";

    private PlayerWeightlessness() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        update(event.getEntity());
    }

    public static void update(Player player) {
        if (RunningBeams.grips(player)) {
            if (!player.isNoGravity()) {
                player.setNoGravity(true);
                player.getPersistentData().putBoolean(TAKEN, true);
            }
        } else if (player.isNoGravity() && player.getPersistentData().getBoolean(TAKEN)) {
            player.setNoGravity(false);
            player.getPersistentData().remove(TAKEN);
        }
    }
}
