package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.debug.GasPressureOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** A snapshot of gas node pressures for the debug overlay. */
public record GasDebugS2CPacket(List<Sample> samples) implements CustomPacketPayload {

    public record Sample(BlockPos pos, float pressure) {
    }

    public static final Type<GasDebugS2CPacket> TYPE = new Type<>(ZPSMod.resource("gas_debug"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Sample> SAMPLE_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, Sample::pos,
                    ByteBufCodecs.FLOAT, Sample::pressure,
                    Sample::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, GasDebugS2CPacket> STREAM_CODEC =
            StreamCodec.composite(
                    SAMPLE_CODEC.apply(ByteBufCodecs.list()), GasDebugS2CPacket::samples,
                    GasDebugS2CPacket::new);

    public static void handle(GasDebugS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> GasPressureOverlay.accept(packet.samples()));
    }

    @Override
    public Type<GasDebugS2CPacket> type() {
        return TYPE;
    }
}
