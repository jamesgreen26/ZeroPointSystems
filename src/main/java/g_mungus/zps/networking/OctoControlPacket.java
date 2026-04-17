package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.OctoMountingEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OctoControlPacket(int a, int b, int c, int d, int e, int f, int g, int h) implements CustomPacketPayload {
    public static final Type<OctoControlPacket> TYPE = new Type<>(ZPSMod.resource("octo_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OctoControlPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OctoControlPacket decode(RegistryFriendlyByteBuf buffer) {
            return new OctoControlPacket(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, OctoControlPacket packet) {
            buffer.writeInt(packet.a);
            buffer.writeInt(packet.b);
            buffer.writeInt(packet.c);
            buffer.writeInt(packet.d);
            buffer.writeInt(packet.e);
            buffer.writeInt(packet.f);
            buffer.writeInt(packet.g);
            buffer.writeInt(packet.h);
        }
    };

    public static void handle(OctoControlPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof OctoMountingEntity seat && seat.isController && seat.blockEntity != null) {
                seat.blockEntity.setA(packet.a());
                seat.blockEntity.setB(packet.b());
                seat.blockEntity.setC(packet.c());
                seat.blockEntity.setD(packet.d());
                seat.blockEntity.setE(packet.e());
                seat.blockEntity.setF(packet.f());
                seat.blockEntity.setG(packet.g());
                seat.blockEntity.setH(packet.h());
            }
        });
    }

    @Override
    public Type<OctoControlPacket> type() {
        return TYPE;
    }
}
