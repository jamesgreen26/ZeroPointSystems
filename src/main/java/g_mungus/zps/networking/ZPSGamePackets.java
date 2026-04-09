package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
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
    }
}
