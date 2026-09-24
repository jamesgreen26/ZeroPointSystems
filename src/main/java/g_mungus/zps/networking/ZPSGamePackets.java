package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ZPSGamePackets {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** To every player watching the chunk at chunkPos; nothing is sent when the chunk is not loaded. */
    public static void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, Object packet) {
        if (level.hasChunk(chunkPos.x, chunkPos.z)) {
            INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(chunkPos.x, chunkPos.z)), packet);
        }
    }

    public static void register() {
        INSTANCE.messageBuilder(OctoControlPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OctoControlPacket::encode)
                .decoder(OctoControlPacket::decode)
                .consumerMainThread(OctoControlPacket::handle)
                .add();

        INSTANCE.messageBuilder(DodecaControlPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DodecaControlPacket::encode)
                .decoder(DodecaControlPacket::decode)
                .consumerMainThread(DodecaControlPacket::handle)
                .add();

        INSTANCE.messageBuilder(ScriptComputerC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScriptComputerC2SPacket::encode)
                .decoder(ScriptComputerC2SPacket::decode)
                .consumerMainThread(ScriptComputerC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(ScriptComputerS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScriptComputerS2CPacket::encode)
                .decoder(ScriptComputerS2CPacket::decode)
                .consumerMainThread(ScriptComputerS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(LoudspeakerTtsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LoudspeakerTtsPacket::encode)
                .decoder(LoudspeakerTtsPacket::decode)
                .consumerMainThread(LoudspeakerTtsPacket::handle)
                .add();

        INSTANCE.messageBuilder(ExecutorBlocksS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ExecutorBlocksS2CPacket::encode)
                .decoder(ExecutorBlocksS2CPacket::decode)
                .consumerMainThread(ExecutorBlocksS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(GetterBlocksS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GetterBlocksS2CPacket::encode)
                .decoder(GetterBlocksS2CPacket::decode)
                .consumerMainThread(GetterBlocksS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestHudInfoC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestHudInfoC2SPacket::encode)
                .decoder(RequestHudInfoC2SPacket::decode)
                .consumerMainThread(RequestHudInfoC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(HudInfoS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HudInfoS2CPacket::encode)
                .decoder(HudInfoS2CPacket::decode)
                .consumerMainThread(HudInfoS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(AddressPadAddPositionC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddressPadAddPositionC2SPacket::encode)
                .decoder(AddressPadAddPositionC2SPacket::decode)
                .consumerMainThread(AddressPadAddPositionC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(AddressPadRemovePositionC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddressPadRemovePositionC2SPacket::encode)
                .decoder(AddressPadRemovePositionC2SPacket::decode)
                .consumerMainThread(AddressPadRemovePositionC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(AddressPadSetEntriesC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddressPadSetEntriesC2SPacket::encode)
                .decoder(AddressPadSetEntriesC2SPacket::decode)
                .consumerMainThread(AddressPadSetEntriesC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(GasNodeSyncS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GasNodeSyncS2CPacket::encode)
                .decoder(GasNodeSyncS2CPacket::decode)
                .consumerMainThread(GasNodeSyncS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestGasDebugC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestGasDebugC2SPacket::encode)
                .decoder(RequestGasDebugC2SPacket::decode)
                .consumerMainThread(RequestGasDebugC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(GasDebugS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GasDebugS2CPacket::encode)
                .decoder(GasDebugS2CPacket::decode)
                .consumerMainThread(GasDebugS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(CreativeGasGeneratorSettingsC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreativeGasGeneratorSettingsC2SPacket::encode)
                .decoder(CreativeGasGeneratorSettingsC2SPacket::decode)
                .consumerMainThread(CreativeGasGeneratorSettingsC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(ReactorShapeS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReactorShapeS2CPacket::encode)
                .decoder(ReactorShapeS2CPacket::decode)
                .consumerMainThread(ReactorShapeS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ReactorStateS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReactorStateS2CPacket::encode)
                .decoder(ReactorStateS2CPacket::decode)
                .consumerMainThread(ReactorStateS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ReactorRemovedS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ReactorRemovedS2CPacket::encode)
                .decoder(ReactorRemovedS2CPacket::decode)
                .consumerMainThread(ReactorRemovedS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(ReactorPortSettingsC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ReactorPortSettingsC2SPacket::encode)
                .decoder(ReactorPortSettingsC2SPacket::decode)
                .consumerMainThread(ReactorPortSettingsC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(DuctCycleC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DuctCycleC2SPacket::encode)
                .decoder(DuctCycleC2SPacket::decode)
                .consumerMainThread(DuctCycleC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(DuctTravelStateS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DuctTravelStateS2CPacket::encode)
                .decoder(DuctTravelStateS2CPacket::decode)
                .consumerMainThread(DuctTravelStateS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(DuctCycleRefusedS2CPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DuctCycleRefusedS2CPacket::encode)
                .decoder(DuctCycleRefusedS2CPacket::decode)
                .consumerMainThread(DuctCycleRefusedS2CPacket::handle)
                .add();

        INSTANCE.messageBuilder(SerialBusSettingsC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SerialBusSettingsC2SPacket::encode)
                .decoder(SerialBusSettingsC2SPacket::decode)
                .consumerMainThread(SerialBusSettingsC2SPacket::handle)
                .add();

        INSTANCE.messageBuilder(AssemblerPatternCellC2SPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AssemblerPatternCellC2SPacket::encode)
                .decoder(AssemblerPatternCellC2SPacket::decode)
                .consumerMainThread(AssemblerPatternCellC2SPacket::handle)
                .add();
    }
}
