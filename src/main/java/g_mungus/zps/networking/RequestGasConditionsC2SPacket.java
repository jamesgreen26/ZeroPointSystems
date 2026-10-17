package g_mungus.zps.networking;

import g_mungus.zps.gas.AtmosphereBreather;
import g_mungus.zps.gas.api.GasConditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The debug screen is open and wants the gas conditions the server last applied to the player. */
public record RequestGasConditionsC2SPacket() {

    public static void encode(RequestGasConditionsC2SPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestGasConditionsC2SPacket decode(FriendlyByteBuf buffer) {
        return new RequestGasConditionsC2SPacket();
    }

    public static void handle(RequestGasConditionsC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            GasConditions conditions = player == null ? null : ((AtmosphereBreather) player).zps$getGasConditions();
            if (conditions != null) {
                ZPSGamePackets.sendToPlayer(player, new GasConditionsS2CPacket(conditions));
            }
        });
        context.setPacketHandled(true);
    }
}
