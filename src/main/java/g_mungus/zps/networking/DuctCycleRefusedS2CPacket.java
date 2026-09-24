package g_mungus.zps.networking;

import g_mungus.zps.client.DuctTravelFade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server has turned down a request for another vent: nothing moved, and nothing will.
 *
 * <p>The client starts fading to black as soon as the key is pressed, before the server has said
 * anything, and comes back only when told it has arrived. A request that is dropped without an
 * answer therefore leaves the rider in the dark until the fade gives up on its own, several seconds
 * later, with the progress bar sitting empty. So every refusal is answered with this, and the
 * client turns the fade round from wherever it has got to.
 */
public record DuctCycleRefusedS2CPacket() {

    public static void encode(DuctCycleRefusedS2CPacket packet, FriendlyByteBuf buffer) {
    }

    public static DuctCycleRefusedS2CPacket decode(FriendlyByteBuf buffer) {
        return new DuctCycleRefusedS2CPacket();
    }

    public static void handle(DuctCycleRefusedS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(DuctTravelFade::refused);
        context.setPacketHandled(true);
    }
}
