package g_mungus.zps.client.debug;

import g_mungus.zps.gas.api.GasConditions;
import g_mungus.zps.networking.RequestGasConditionsC2SPacket;
import g_mungus.zps.networking.ZPSGamePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The gas conditions the server last applied to this player, for the debug screen. Asked for only
 * while the screen is open, every {@link #REQUEST_INTERVAL_TICKS}; an answer older than
 * {@link #STALE_TICKS} is not shown.
 */
public final class ClientGasConditions {
    private static final int REQUEST_INTERVAL_TICKS = 10;
    private static final int STALE_TICKS = 40;

    private static @Nullable GasConditions latest;
    private static long receivedAt = Long.MIN_VALUE / 2;
    private static long requestedAt = Long.MIN_VALUE / 2;

    private ClientGasConditions() {
    }

    public static void receive(GasConditions conditions) {
        latest = conditions;
        receivedAt = gameTime();
    }

    /** The latest conditions, asking for fresh ones if it is time; null if there are none recent enough. */
    public static @Nullable GasConditions current() {
        long now = gameTime();
        if (now - requestedAt >= REQUEST_INTERVAL_TICKS || now < requestedAt) {
            requestedAt = now;
            ZPSGamePackets.sendToServer(new RequestGasConditionsC2SPacket());
        }
        return now - receivedAt <= STALE_TICKS && now >= receivedAt ? latest : null;
    }

    private static long gameTime() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }
}
