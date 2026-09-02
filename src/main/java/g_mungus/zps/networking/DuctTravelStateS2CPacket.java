package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.DuctTravelClientState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

/**
 * The vents on the run the player is currently inside, and which of them they are looking out of.
 *
 * <p>Only the rider needs any of this, so it is pushed to that one player rather than synched on
 * the entity.
 *
 * <p>{@code orientTo} is present only when the client should turn the player to look straight out
 * of the grille, which is on climbing in and nowhere else: you enter facing the panel, so without
 * it you would arrive staring into the back of a duct. Once inside, where you are looking is yours
 * — taking it away mid-hop is disorienting — so a hop leaves it empty and the view is untouched.
 */
public record DuctTravelStateS2CPacket(List<BlockPos> destinations, int selected,
                                       Optional<Direction> orientTo)
        implements CustomPacketPayload {

    public static final Type<DuctTravelStateS2CPacket> TYPE =
            new Type<>(ZPSMod.resource("duct_travel_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DuctTravelStateS2CPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    DuctTravelStateS2CPacket::destinations,
                    ByteBufCodecs.VAR_INT, DuctTravelStateS2CPacket::selected,
                    ByteBufCodecs.optional(Direction.STREAM_CODEC),
                    DuctTravelStateS2CPacket::orientTo,
                    DuctTravelStateS2CPacket::new);

    public static void handle(DuctTravelStateS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DuctTravelClientState.accept(packet));
    }

    @Override
    public Type<DuctTravelStateS2CPacket> type() {
        return TYPE;
    }
}
