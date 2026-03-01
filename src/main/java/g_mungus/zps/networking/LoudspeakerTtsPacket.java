package g_mungus.zps.networking;

import g_mungus.zps.client.tts.TtsSoundsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record LoudspeakerTtsPacket(BlockPos pos, String message) {

    public static void encode(LoudspeakerTtsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.message);
    }

    public static LoudspeakerTtsPacket decode(FriendlyByteBuf buffer) {
        return new LoudspeakerTtsPacket(buffer.readBlockPos(), buffer.readUtf());
    }

    public static void handle(LoudspeakerTtsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> TtsSoundsManager.speakTextAt(packet.pos, packet.message));
        context.setPacketHandled(true);
    }
}