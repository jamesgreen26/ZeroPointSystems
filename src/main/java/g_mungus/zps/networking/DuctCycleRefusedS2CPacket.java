package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.DuctTravelFade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The server has turned down a request for another vent: nothing moved, and nothing will.
 *
 * <p>The client starts fading to black as soon as the key is pressed, before the server has said
 * anything, and comes back only when told it has arrived. A request that is dropped without an
 * answer therefore leaves the rider in the dark until the fade gives up on its own, several seconds
 * later, with the progress bar sitting empty. So every refusal is answered with this, and the
 * client turns the fade round from wherever it has got to.
 */
public record DuctCycleRefusedS2CPacket() implements CustomPacketPayload {

    public static final Type<DuctCycleRefusedS2CPacket> TYPE =
            new Type<>(ZPSMod.resource("duct_cycle_refused"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DuctCycleRefusedS2CPacket> STREAM_CODEC =
            StreamCodec.unit(new DuctCycleRefusedS2CPacket());

    public static void handle(DuctCycleRefusedS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(DuctTravelFade::refused);
    }

    @Override
    public Type<DuctCycleRefusedS2CPacket> type() {
        return TYPE;
    }
}
