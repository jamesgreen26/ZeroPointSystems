package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ZPSGamePackets {
    public static void onRegisterPayloadHandler(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ZPSMod.MOD_ID)
                .versioned("1.0")
                .optional();

        registrar.playToServer(OctovariantControlPacket.TYPE, OctovariantControlPacket.STREAM_CODEC, OctovariantControlPacket::handle);
    }
}
