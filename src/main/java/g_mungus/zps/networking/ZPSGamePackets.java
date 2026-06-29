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
        registrar.playToServer(AddressPadAddPositionC2SPacket.TYPE, AddressPadAddPositionC2SPacket.STREAM_CODEC, AddressPadAddPositionC2SPacket::handle);
        registrar.playToServer(AddressPadRemovePositionC2SPacket.TYPE, AddressPadRemovePositionC2SPacket.STREAM_CODEC, AddressPadRemovePositionC2SPacket::handle);
        registrar.playToServer(AddressPadSetEntriesC2SPacket.TYPE, AddressPadSetEntriesC2SPacket.STREAM_CODEC, AddressPadSetEntriesC2SPacket::handle);
        registrar.playToServer(RoboticArmSettingsC2SPacket.TYPE, RoboticArmSettingsC2SPacket.STREAM_CODEC, RoboticArmSettingsC2SPacket::handle);
        registrar.playToClient(RoboticArmOpenS2CPacket.TYPE, RoboticArmOpenS2CPacket.STREAM_CODEC, RoboticArmOpenS2CPacket::handle);
        registrar.playToServer(ContraptionBreakC2SPacket.TYPE, ContraptionBreakC2SPacket.STREAM_CODEC, ContraptionBreakC2SPacket::handle);
        registrar.playToServer(ContraptionPlaceC2SPacket.TYPE, ContraptionPlaceC2SPacket.STREAM_CODEC, ContraptionPlaceC2SPacket::handle);
        registrar.playToServer(ContraptionUseC2SPacket.TYPE, ContraptionUseC2SPacket.STREAM_CODEC, ContraptionUseC2SPacket::handle);
        registrar.playToServer(ContraptionBreakProgressC2SPacket.TYPE, ContraptionBreakProgressC2SPacket.STREAM_CODEC, ContraptionBreakProgressC2SPacket::handle);
        registrar.playToClient(ContraptionDestroyStageS2CPacket.TYPE, ContraptionDestroyStageS2CPacket.STREAM_CODEC, ContraptionDestroyStageS2CPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
