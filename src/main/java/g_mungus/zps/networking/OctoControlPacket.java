package g_mungus.zps.networking;

import g_mungus.zps.entity.OctoMountingEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OctoControlPacket(int a, int b, int c, int d, int e, int f, int g, int h) {

    public static void encode(OctoControlPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.a);
        buffer.writeInt(packet.b);
        buffer.writeInt(packet.c);
        buffer.writeInt(packet.d);
        buffer.writeInt(packet.e);
        buffer.writeInt(packet.f);
        buffer.writeInt(packet.g);
        buffer.writeInt(packet.h);
    }

    public static OctoControlPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(OctoControlPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                if (context.getSender().getVehicle() instanceof OctoMountingEntity seat) {
                    if (seat.isController && seat.blockEntity != null) {
                        seat.blockEntity.setA(packet.a());
                        seat.blockEntity.setB(packet.b());
                        seat.blockEntity.setC(packet.c());
                        seat.blockEntity.setD(packet.d());
                        seat.blockEntity.setE(packet.e());
                        seat.blockEntity.setF(packet.f());
                        seat.blockEntity.setG(packet.g());
                        seat.blockEntity.setH(packet.h());
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
