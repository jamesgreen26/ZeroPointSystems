package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.screens.RoboticArmClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Tells the client to open the Robotic Arm screen. Server-driven (like the Script Terminal) so it
 * works both in the world and on a contraption — {@code motorPos} is the host Servo Motor when the
 * arm rides a contraption, else {@code null}.
 */
public record RoboticArmOpenS2CPacket(BlockPos pos, @Nullable BlockPos motorPos) implements CustomPacketPayload {
    public static final Type<RoboticArmOpenS2CPacket> TYPE = new Type<>(ZPSMod.resource("robotic_arm_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoboticArmOpenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RoboticArmOpenS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new RoboticArmOpenS2CPacket(buffer.readBlockPos(), buffer.readBoolean() ? buffer.readBlockPos() : null);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RoboticArmOpenS2CPacket packet) {
            buffer.writeBlockPos(packet.pos);
            buffer.writeBoolean(packet.motorPos != null);
            if (packet.motorPos != null) {
                buffer.writeBlockPos(packet.motorPos);
            }
        }
    };

    public static void handle(RoboticArmOpenS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> RoboticArmClientHooks.openRoboticArmScreen(packet.pos, packet.motorPos));
    }

    @Override
    public Type<RoboticArmOpenS2CPacket> type() {
        return TYPE;
    }
}
