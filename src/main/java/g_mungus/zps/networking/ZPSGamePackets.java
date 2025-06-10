package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.simple.SimpleChannel;

public class ZPSGamePackets {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ZPSMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int packetId = 0;

    public static void register() {
        INSTANCE.messageBuilder(OctovariantControlPacket.class, packetId++)
            .encoder(OctovariantControlPacket::encode)
            .decoder(OctovariantControlPacket::decode)
            .consumerMainThread(OctovariantControlPacket::handle)
            .add();
    }
}
