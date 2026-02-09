package g_mungus.zps.networking;

import g_mungus.zps.client.screens.ScriptTerminalScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ScriptComputerS2CPacket(BlockPos computerPos, boolean loop, String contents) {

    public static void encode(ScriptComputerS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.computerPos);
        buffer.writeBoolean(packet.loop);
        buffer.writeUtf(packet.contents);
    }

    public static ScriptComputerS2CPacket decode(FriendlyByteBuf buffer) {
        return new ScriptComputerS2CPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readUtf()
        );
    }

    public static void handle(ScriptComputerS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // This runs on the client side
            ScriptTerminalScreen.openWithData(packet.computerPos, packet.contents, packet.loop);
        });
        context.setPacketHandled(true);
    }
}