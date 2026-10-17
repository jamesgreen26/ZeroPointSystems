package g_mungus.zps.networking;

import g_mungus.zps.gas.AtmosphereBreather;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player has started or stopped drowning in gas. Air runs on both sides, and a client that did
 * not know would refill what the server drains, flickering the bubbles; see {@code GasExposure}.
 * Sent only on a change.
 */
public record DrowningS2CPacket(boolean drowning) {

    public static void encode(DrowningS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.drowning);
    }

    public static DrowningS2CPacket decode(FriendlyByteBuf buffer) {
        return new DrowningS2CPacket(buffer.readBoolean());
    }

    public static void handle(DrowningS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ((AtmosphereBreather) Minecraft.getInstance().player).zps$setSyncedDrowning(packet.drowning());
            }
        });
        context.setPacketHandled(true);
    }
}
