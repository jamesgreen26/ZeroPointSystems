package g_mungus.zps.networking;

import g_mungus.zps.entity.DodecaMountingEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DodecaControlPacket(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l) {

    public static void encode(DodecaControlPacket packet, FriendlyByteBuf buffer) {
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

    public static DodecaControlPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(DodecaControlPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                if (context.getSender().getVehicle() instanceof DodecaMountingEntity seat) {
                    if (seat.isController && seat.blockEntity != null) {
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
                }
            }
        });
        context.setPacketHandled(true);
    }
}
