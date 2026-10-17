package g_mungus.zps.networking;

import g_mungus.zps.gas.GasJet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;
import org.valkyrienskies.kelvin.util.KelvinExtensions;

import java.util.function.Supplier;

/**
 * A sealed room came open at {@code pos}: a burst of its gas blows out through the hole along
 * {@code outward}. {@code massKg} is the share of the room's gas lost through this hole.
 */
public record RoomBreachS2CPacket(BlockPos pos, Direction outward, ResourceLocation gas, float massKg) {

    public static void encode(RoomBreachS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeEnum(packet.outward);
        buffer.writeResourceLocation(packet.gas);
        buffer.writeFloat(packet.massKg);
    }

    public static RoomBreachS2CPacket decode(FriendlyByteBuf buffer) {
        return new RoomBreachS2CPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class),
                buffer.readResourceLocation(), buffer.readFloat());
    }

    /** Kilograms of escaping gas each particle stands for. */
    private static final double MASS_PER_PARTICLE = 0.05;
    /** Jets fired per puff, each slower than the last, so the burst trails off rather than ending in a ring. */
    private static final int WAVES = 3;

    public static void handle(RoomBreachS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            GasType gas = GasTypeRegistry.INSTANCE.getGasType(packet.gas());
            if (gas == null) {
                return;
            }
            double particles = packet.massKg() / MASS_PER_PARTICLE / WAVES;
            for (int wave = 0; wave < WAVES; wave++) {
                GasJet.spawn(level, KelvinExtensions.INSTANCE.toDuctNodePos(packet.pos(), level.dimension().location()),
                        gas, Vec3.atCenterOf(packet.pos()), packet.outward(), 0.35 / (wave + 1),
                        Math.max(particles, 4), 0.45, -0.5);
            }
        });
        context.setPacketHandled(true);
    }
}
