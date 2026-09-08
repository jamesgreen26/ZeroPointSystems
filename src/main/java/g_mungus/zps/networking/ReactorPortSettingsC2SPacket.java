package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
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

/** What the reactor port's screen sends back on Done: the direction and the gas filter. */
public record ReactorPortSettingsC2SPacket(BlockPos blockPos, ReactorPortMode mode, GasFilter filter)
        implements CustomPacketPayload {
    public static final Type<ReactorPortSettingsC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("reactor_port_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReactorPortSettingsC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ReactorPortSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new ReactorPortSettingsC2SPacket(
                            buffer.readBlockPos(),
                            buffer.readEnum(ReactorPortMode.class),
                            GasFilter.read(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ReactorPortSettingsC2SPacket packet) {
                    buffer.writeBlockPos(packet.blockPos);
                    buffer.writeEnum(packet.mode);
                    packet.filter.write(buffer);
                }
            };

    public static void handle(ReactorPortSettingsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof ReactorPortBlockEntity port)) {
                return;
            }
            // Ship-aware, so a port riding a Valkyrien Skies ship measures the right distance.
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            port.setSettings(packet.mode, packet.filter);
        });
    }

    @Override
    public Type<ReactorPortSettingsC2SPacket> type() {
        return TYPE;
    }
}
