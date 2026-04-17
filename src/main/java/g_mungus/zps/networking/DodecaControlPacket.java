package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.entity.DodecaMountingEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DodecaControlPacket(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l) implements CustomPacketPayload {
    public static final Type<DodecaControlPacket> TYPE = new Type<>(ZPSMod.resource("dodeca_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DodecaControlPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DodecaControlPacket decode(RegistryFriendlyByteBuf buffer) {
            return new DodecaControlPacket(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
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
        public void encode(RegistryFriendlyByteBuf buffer, DodecaControlPacket packet) {
            buffer.writeInt(packet.a);
            buffer.writeInt(packet.b);
            buffer.writeInt(packet.c);
            buffer.writeInt(packet.d);
            buffer.writeInt(packet.e);
            buffer.writeInt(packet.f);
            buffer.writeInt(packet.g);
            buffer.writeInt(packet.h);
            buffer.writeInt(packet.i);
            buffer.writeInt(packet.j);
            buffer.writeInt(packet.k);
            buffer.writeInt(packet.l);
        }
    };

    public static void handle(DodecaControlPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof DodecaMountingEntity seat && seat.isController && seat.blockEntity != null) {
                seat.blockEntity.setA(packet.a());
                seat.blockEntity.setB(packet.b());
                seat.blockEntity.setC(packet.c());
                seat.blockEntity.setD(packet.d());
                seat.blockEntity.setE(packet.e());
                seat.blockEntity.setF(packet.f());
                seat.blockEntity.setG(packet.g());
                seat.blockEntity.setH(packet.h());
                seat.blockEntity.setI(packet.i());
                seat.blockEntity.setJ(packet.j());
                seat.blockEntity.setK(packet.k());
                seat.blockEntity.setL(packet.l());
            }
        });
    }

    @Override
    public Type<DodecaControlPacket> type() {
        return TYPE;
    }
}
