package g_mungus.zps.networking;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ZPSGamePackets {
    private static final String PROTOCOL_VERSION = "1";

    private ZPSGamePackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(OctoControlPacket.TYPE, OctoControlPacket.STREAM_CODEC, OctoControlPacket::handle);
        registrar.playToServer(DodecaControlPacket.TYPE, DodecaControlPacket.STREAM_CODEC, DodecaControlPacket::handle);
        registrar.playToServer(ScriptComputerC2SPacket.TYPE, ScriptComputerC2SPacket.STREAM_CODEC, ScriptComputerC2SPacket::handle);
        registrar.playToServer(RequestHudInfoC2SPacket.TYPE, RequestHudInfoC2SPacket.STREAM_CODEC, RequestHudInfoC2SPacket::handle);
        registrar.playToClient(ScriptComputerS2CPacket.TYPE, ScriptComputerS2CPacket.STREAM_CODEC, ScriptComputerS2CPacket::handle);
        registrar.playToClient(LoudspeakerTtsPacket.TYPE, LoudspeakerTtsPacket.STREAM_CODEC, LoudspeakerTtsPacket::handle);
        registrar.playToClient(ExecutorBlocksS2CPacket.TYPE, ExecutorBlocksS2CPacket.STREAM_CODEC, ExecutorBlocksS2CPacket::handle);
        registrar.playToClient(GetterBlocksS2CPacket.TYPE, GetterBlocksS2CPacket.STREAM_CODEC, GetterBlocksS2CPacket::handle);
        registrar.playToClient(HudInfoS2CPacket.TYPE, HudInfoS2CPacket.STREAM_CODEC, HudInfoS2CPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
