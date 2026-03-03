package g_mungus.zps.networking;

import g_mungus.zps.client.screens.ScriptTerminalScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public record ScriptComputerS2CPacket(BlockPos computerPos, boolean loop, int delay, String contents, Set<ResourceLocation> connectedBlocks) {

    public static void encode(ScriptComputerS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.computerPos);
        buffer.writeBoolean(packet.loop);
        buffer.writeInt(packet.delay);
        buffer.writeUtf(packet.contents);
        buffer.writeCollection(packet.connectedBlocks, FriendlyByteBuf::writeResourceLocation);
    }

    public static ScriptComputerS2CPacket decode(FriendlyByteBuf buffer) {
        return new ScriptComputerS2CPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readUtf(),
                new HashSet<>(buffer.readList(FriendlyByteBuf::readResourceLocation))
        );
    }

    public static void handle(ScriptComputerS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // This runs on the client side
            ScriptTerminalScreen.openWithData(packet.computerPos, packet.contents, packet.loop, packet.delay, packet.connectedBlocks);
        });
        context.setPacketHandled(true);
    }
}