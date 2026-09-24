package g_mungus.zps.client;

import g_mungus.zps.entity.DuctTravelEntity;
import g_mungus.zps.networking.DuctTravelStateS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * What the local player's HUD knows about the duct run they are inside.
 *
 * <p>The server owns the real list; this is the copy the overlay draws from, replaced wholesale
 * every time the selection changes. It also aims the player's view out of the grille on the way
 * in, which is the only time anything here touches where they are looking.
 */
public final class DuctTravelClientState {

    private static List<BlockPos> destinations = List.of();
    private static int selected;

    private DuctTravelClientState() {
    }

    public static void accept(DuctTravelStateS2CPacket packet) {
        destinations = packet.destinations();
        selected = packet.selected();

        // Only ever set on the way in — a hop leaves the player's view exactly where they left it.
        packet.orientTo().ifPresent(DuctTravelClientState::lookOut);

        // Does nothing on the way in, where there was no fade to come back from.
        DuctTravelFade.arrived();
    }

    /** Turn the player to look straight out of the grille they have just climbed into. */
    private static void lookOut(Direction facing) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof DuctTravelEntity)) {
            return;
        }
        float yaw = DuctTravelEntity.yawFor(facing);

        player.setYRot(yaw);
        player.setXRot(DuctTravelEntity.pitchFor(facing));
        // The head is what everyone else sees of a rider, and it tracks yHeadRot rather than yRot.
        // Both, and their previous values, so climbing in is a clean cut with nothing swinging
        // round from wherever they happened to be looking outside.
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setOldPosAndRot();
    }

    /** Dropped when the player leaves a duct, so a stale list never flashes up on the next entry. */
    public static void clear() {
        destinations = List.of();
        selected = 0;
    }

    public static List<BlockPos> destinations() {
        return destinations;
    }

    public static int selected() {
        return selected;
    }

    public static boolean isEmpty() {
        return destinations.isEmpty();
    }
}
