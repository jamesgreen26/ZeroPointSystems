package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.gas.GasJet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;
import org.valkyrienskies.kelvin.util.KelvinExtensions;

/**
 * A sealed room came open at {@code pos}: a burst of its gas blows out through the hole along
 * {@code outward}. {@code massKg} is the share of the room's gas lost through this hole.
 */
public record RoomBreachS2CPacket(BlockPos pos, Direction outward, ResourceLocation gas, float massKg)
        implements CustomPacketPayload {

    public static final Type<RoomBreachS2CPacket> TYPE = new Type<>(ZPSMod.resource("room_breach"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomBreachS2CPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomBreachS2CPacket::pos,
            Direction.STREAM_CODEC, RoomBreachS2CPacket::outward,
            ResourceLocation.STREAM_CODEC, RoomBreachS2CPacket::gas,
            ByteBufCodecs.FLOAT, RoomBreachS2CPacket::massKg,
            RoomBreachS2CPacket::new);

    /** Kilograms of escaping gas each particle stands for. */
    private static final double MASS_PER_PARTICLE = 0.05;
    /** Jets fired per puff, each slower than the last, so the burst trails off rather than ending in a ring. */
    private static final int WAVES = 3;

    public static void handle(RoomBreachS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
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
    }

    @Override
    public Type<RoomBreachS2CPacket> type() {
        return TYPE;
    }
}
