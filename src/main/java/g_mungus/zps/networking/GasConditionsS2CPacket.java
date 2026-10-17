package g_mungus.zps.networking;

import g_mungus.zps.client.debug.ClientGasConditions;
import g_mungus.zps.gas.api.GasConditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The gas conditions the server last applied to the player, for the debug screen. */
public record GasConditionsS2CPacket(GasConditions conditions) {

    public static void encode(GasConditionsS2CPacket packet, FriendlyByteBuf buffer) {
        GasConditions.write(buffer, packet.conditions);
    }

    public static GasConditionsS2CPacket decode(FriendlyByteBuf buffer) {
        return new GasConditionsS2CPacket(GasConditions.read(buffer));
    }

    public static void handle(GasConditionsS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientGasConditions.receive(packet.conditions()));
        context.setPacketHandled(true);
    }
}
