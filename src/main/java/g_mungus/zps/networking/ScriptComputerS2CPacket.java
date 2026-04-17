package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.screens.ScriptTerminalScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

public record ScriptComputerS2CPacket(BlockPos computerPos, boolean loop, int delay, String contents, Set<ResourceLocation> connectedBlocks) implements CustomPacketPayload {
    public static final Type<ScriptComputerS2CPacket> TYPE = new Type<>(ZPSMod.resource("script_computer_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScriptComputerS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ScriptComputerS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ScriptComputerS2CPacket(
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readUtf(),
                    new HashSet<>(buffer.readList(buf -> buf.readResourceLocation()))
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ScriptComputerS2CPacket packet) {
            buffer.writeBlockPos(packet.computerPos);
            buffer.writeBoolean(packet.loop);
            buffer.writeInt(packet.delay);
            buffer.writeUtf(packet.contents);
            buffer.writeCollection(packet.connectedBlocks, (buf, id) -> buf.writeResourceLocation(id));
        }
    };

    public static void handle(ScriptComputerS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ScriptTerminalScreen.openWithData(packet.computerPos, packet.contents, packet.loop, packet.delay, packet.connectedBlocks));
    }

    @Override
    public Type<ScriptComputerS2CPacket> type() {
        return TYPE;
    }
}
