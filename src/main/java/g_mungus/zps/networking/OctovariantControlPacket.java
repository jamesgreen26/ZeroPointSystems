package g_mungus.zps.networking;

import g_mungus.zps.entity.OctoMountingEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OctovariantControlPacket {
    private final int a;
    private final int b;
    private final int c;
    private final int d;
    private final int e;
    private final int f;
    private final int g;
    private final int h;

    public OctovariantControlPacket(int a, int b, int c, int d, int e, int f, int g, int h) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
        this.g = g;
        this.h = h;
    }

    public static void encode(OctovariantControlPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.a);
        buffer.writeInt(packet.b);
        buffer.writeInt(packet.c);
        buffer.writeInt(packet.d);
        buffer.writeInt(packet.e);
        buffer.writeInt(packet.f);
        buffer.writeInt(packet.g);
        buffer.writeInt(packet.h);
    }

    public static OctovariantControlPacket decode(FriendlyByteBuf buffer) {
        return new OctovariantControlPacket(
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

    public static void handle(OctovariantControlPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
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

    public int a() { return a; }
    public int b() { return b; }
    public int c() { return c; }
    public int d() { return d; }
    public int e() { return e; }
    public int f() { return f; }
    public int g() { return g; }
    public int h() { return h; }
}
