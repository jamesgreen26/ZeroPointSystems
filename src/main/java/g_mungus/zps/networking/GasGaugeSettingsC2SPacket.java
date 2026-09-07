package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the gas gauge's screen sends back whenever its mode or bounds change. */
public record GasGaugeSettingsC2SPacket(BlockPos blockPos, GasGaugeBlockEntity.Mode mode,
                                        double lower, double upper) implements CustomPacketPayload {
    public static final Type<GasGaugeSettingsC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("gas_gauge_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasGaugeSettingsC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public GasGaugeSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new GasGaugeSettingsC2SPacket(
                            buffer.readBlockPos(),
                            buffer.readEnum(GasGaugeBlockEntity.Mode.class),
                            buffer.readDouble(),
                            buffer.readDouble());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, GasGaugeSettingsC2SPacket packet) {
                    buffer.writeBlockPos(packet.blockPos);
                    buffer.writeEnum(packet.mode);
                    buffer.writeDouble(packet.lower);
                    buffer.writeDouble(packet.upper);
                }
            };

    public static void handle(GasGaugeSettingsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof GasGaugeBlockEntity gauge)) {
                return;
            }
            // Ship-aware, so a gauge riding a Valkyrien Skies ship measures the right distance.
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            gauge.setSettings(packet.mode, packet.lower, packet.upper);
        });
    }

    @Override
    public Type<GasGaugeSettingsC2SPacket> type() {
        return TYPE;
    }
}
