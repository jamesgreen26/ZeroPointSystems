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
        registrar.playToServer(ControllerInputC2SPacket.TYPE, ControllerInputC2SPacket.STREAM_CODEC, ControllerInputC2SPacket::handle);
        registrar.playToServer(ControllerKeysC2SPacket.TYPE, ControllerKeysC2SPacket.STREAM_CODEC, ControllerKeysC2SPacket::handle);
        registrar.playToServer(ScriptComputerC2SPacket.TYPE, ScriptComputerC2SPacket.STREAM_CODEC, ScriptComputerC2SPacket::handle);
        registrar.playToServer(RequestHudInfoC2SPacket.TYPE, RequestHudInfoC2SPacket.STREAM_CODEC, RequestHudInfoC2SPacket::handle);
        registrar.playToClient(ScriptComputerS2CPacket.TYPE, ScriptComputerS2CPacket.STREAM_CODEC, ScriptComputerS2CPacket::handle);
        registrar.playToClient(LoudspeakerTtsPacket.TYPE, LoudspeakerTtsPacket.STREAM_CODEC, LoudspeakerTtsPacket::handle);
        registrar.playToClient(ScriptTreeS2CPacket.TYPE, ScriptTreeS2CPacket.STREAM_CODEC, ScriptTreeS2CPacket::handle);
        registrar.playToClient(HudInfoS2CPacket.TYPE, HudInfoS2CPacket.STREAM_CODEC, HudInfoS2CPacket::handle);
        registrar.playToClient(GasNodeSyncS2CPacket.TYPE, GasNodeSyncS2CPacket.STREAM_CODEC, GasNodeSyncS2CPacket::handle);
        registrar.playToServer(RequestGasDebugC2SPacket.TYPE, RequestGasDebugC2SPacket.STREAM_CODEC, RequestGasDebugC2SPacket::handle);
        registrar.playToClient(GasDebugS2CPacket.TYPE, GasDebugS2CPacket.STREAM_CODEC, GasDebugS2CPacket::handle);
        registrar.playToClient(ReactorShapeS2CPacket.TYPE, ReactorShapeS2CPacket.STREAM_CODEC, ReactorShapeS2CPacket::handle);
        registrar.playToClient(ReactorStateS2CPacket.TYPE, ReactorStateS2CPacket.STREAM_CODEC, ReactorStateS2CPacket::handle);
        registrar.playToClient(ReactorRemovedS2CPacket.TYPE, ReactorRemovedS2CPacket.STREAM_CODEC, ReactorRemovedS2CPacket::handle);
        registrar.playToServer(AddressPadAddPositionC2SPacket.TYPE, AddressPadAddPositionC2SPacket.STREAM_CODEC, AddressPadAddPositionC2SPacket::handle);
        registrar.playToServer(AddressPadRemovePositionC2SPacket.TYPE, AddressPadRemovePositionC2SPacket.STREAM_CODEC, AddressPadRemovePositionC2SPacket::handle);
        registrar.playToServer(AddressPadSetEntriesC2SPacket.TYPE, AddressPadSetEntriesC2SPacket.STREAM_CODEC, AddressPadSetEntriesC2SPacket::handle);
        registrar.playToServer(CreativeGasGeneratorSettingsC2SPacket.TYPE, CreativeGasGeneratorSettingsC2SPacket.STREAM_CODEC, CreativeGasGeneratorSettingsC2SPacket::handle);
        registrar.playToServer(ReactorPortSettingsC2SPacket.TYPE, ReactorPortSettingsC2SPacket.STREAM_CODEC, ReactorPortSettingsC2SPacket::handle);
        registrar.playToServer(SerialBusSettingsC2SPacket.TYPE, SerialBusSettingsC2SPacket.STREAM_CODEC, SerialBusSettingsC2SPacket::handle);
        registrar.playToServer(AssemblerPatternCellC2SPacket.TYPE, AssemblerPatternCellC2SPacket.STREAM_CODEC, AssemblerPatternCellC2SPacket::handle);
        registrar.playToServer(DuctCycleC2SPacket.TYPE, DuctCycleC2SPacket.STREAM_CODEC, DuctCycleC2SPacket::handle);
        registrar.playToClient(DuctTravelStateS2CPacket.TYPE, DuctTravelStateS2CPacket.STREAM_CODEC, DuctTravelStateS2CPacket::handle);
        registrar.playToClient(DuctCycleRefusedS2CPacket.TYPE, DuctCycleRefusedS2CPacket.STREAM_CODEC, DuctCycleRefusedS2CPacket::handle);
        registrar.playToClient(DrowningS2CPacket.TYPE, DrowningS2CPacket.STREAM_CODEC, DrowningS2CPacket::handle);
        registrar.playToServer(RequestGasConditionsC2SPacket.TYPE, RequestGasConditionsC2SPacket.STREAM_CODEC, RequestGasConditionsC2SPacket::handle);
        registrar.playToClient(GasConditionsS2CPacket.TYPE, GasConditionsS2CPacket.STREAM_CODEC, GasConditionsS2CPacket::handle);
        registrar.playToClient(RoomBreachS2CPacket.TYPE, RoomBreachS2CPacket.STREAM_CODEC, RoomBreachS2CPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
