package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.OctoMountingEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record OctovariantControlPacket(int a, int b, int c, int d, int e, int f, int g, int h) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "octovariant_control");
    public static final Type<OctovariantControlPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<? super ByteBuf, OctovariantControlPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OctovariantControlPacket decode(ByteBuf buf) {
            int a = ByteBufCodecs.VAR_INT.decode(buf);
            int b = ByteBufCodecs.VAR_INT.decode(buf);
            int c = ByteBufCodecs.VAR_INT.decode(buf);
            int d = ByteBufCodecs.VAR_INT.decode(buf);
            int e = ByteBufCodecs.VAR_INT.decode(buf);
            int f = ByteBufCodecs.VAR_INT.decode(buf);
            int g = ByteBufCodecs.VAR_INT.decode(buf);
            int h = ByteBufCodecs.VAR_INT.decode(buf);
            return new OctovariantControlPacket(a, b, c, d, e, f, g, h);
        }
        @Override
        public void encode(ByteBuf buf, OctovariantControlPacket packet) {
            ByteBufCodecs.VAR_INT.encode(buf, packet.a());
            ByteBufCodecs.VAR_INT.encode(buf, packet.b());
            ByteBufCodecs.VAR_INT.encode(buf, packet.c());
            ByteBufCodecs.VAR_INT.encode(buf, packet.d());
            ByteBufCodecs.VAR_INT.encode(buf, packet.e());
            ByteBufCodecs.VAR_INT.encode(buf, packet.f());
            ByteBufCodecs.VAR_INT.encode(buf, packet.g());
            ByteBufCodecs.VAR_INT.encode(buf, packet.h());
        }
    };

    @Override
    public Type<?> type() {
        return TYPE;
    }

    public static void handle(OctovariantControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                if (player.getVehicle() instanceof OctoMountingEntity seat && seat.isController && seat.blockEntity != null) {
                    seat.blockEntity.setA(packet.a);
                    seat.blockEntity.setB(packet.b);
                    seat.blockEntity.setC(packet.c);
                    seat.blockEntity.setD(packet.d);
                    seat.blockEntity.setE(packet.e);
                    seat.blockEntity.setF(packet.f);
                    seat.blockEntity.setG(packet.g);
                    seat.blockEntity.setH(packet.h);
                }
            }
        });
    }
}
