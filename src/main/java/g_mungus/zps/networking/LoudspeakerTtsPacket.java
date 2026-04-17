package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.tts.TtsSoundsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LoudspeakerTtsPacket(BlockPos pos, String message) implements CustomPacketPayload {
    public static final Type<LoudspeakerTtsPacket> TYPE = new Type<>(ZPSMod.resource("loudspeaker_tts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoudspeakerTtsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LoudspeakerTtsPacket decode(RegistryFriendlyByteBuf buffer) {
            return new LoudspeakerTtsPacket(buffer.readBlockPos(), buffer.readUtf());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, LoudspeakerTtsPacket packet) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeUtf(packet.message);
        }
    };

    public static void handle(LoudspeakerTtsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TtsSoundsManager.speakTextAt(packet.pos, packet.message));
    }

    @Override
    public Type<LoudspeakerTtsPacket> type() {
        return TYPE;
    }
}
