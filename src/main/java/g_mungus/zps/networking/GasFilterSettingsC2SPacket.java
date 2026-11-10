package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.gas.GasFilterBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.gas.GasFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the gas filter's screen sends on every toggle: the whole list of blocked gases. */
public record GasFilterSettingsC2SPacket(BlockPos blockPos, GasFilter filter) implements CustomPacketPayload {
    public static final Type<GasFilterSettingsC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("gas_filter_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasFilterSettingsC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public GasFilterSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new GasFilterSettingsC2SPacket(buffer.readBlockPos(), GasFilter.read(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, GasFilterSettingsC2SPacket packet) {
                    buffer.writeBlockPos(packet.blockPos);
                    packet.filter.write(buffer);
                }
            };

    public static void handle(GasFilterSettingsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof GasFilterBlockEntity filter)) {
                return;
            }
            // Ship-aware, so a filter riding a Valkyrien Skies ship measures the right distance.
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            filter.setFilter(packet.filter);
        });
    }

    @Override
    public Type<GasFilterSettingsC2SPacket> type() {
        return TYPE;
    }
}
