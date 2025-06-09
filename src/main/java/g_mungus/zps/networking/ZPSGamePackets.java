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
        INSTANCE.messageBuilder(OctovariantControlPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OctovariantControlPacket::encode)
                .decoder(OctovariantControlPacket::decode)
                .consumerMainThread(OctovariantControlPacket::handle)
                .add();
    }
}
