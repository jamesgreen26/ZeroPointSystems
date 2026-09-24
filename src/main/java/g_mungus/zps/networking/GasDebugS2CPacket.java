package g_mungus.zps.networking;

import g_mungus.zps.client.debug.GasPressureOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** A snapshot of gas node pressures for the debug overlay. */
public record GasDebugS2CPacket(List<Sample> samples) {

    public record Sample(BlockPos pos, float pressure) {
    }

    public static void encode(GasDebugS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.samples(), (buf, sample) -> {
            buf.writeBlockPos(sample.pos());
            buf.writeFloat(sample.pressure());
        });
    }

    public static GasDebugS2CPacket decode(FriendlyByteBuf buffer) {
        return new GasDebugS2CPacket(buffer.readList(buf -> new Sample(buf.readBlockPos(), buf.readFloat())));
    }

    public static void handle(GasDebugS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> GasPressureOverlay.accept(packet.samples()));
        context.setPacketHandled(true);
    }
}
