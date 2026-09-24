package g_mungus.zps.networking;

import g_mungus.zps.menu.AssemblerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Stamps one cell of an open assembler's ghost pattern with an item the player never held — currently
 * sent when a JEI ingredient is dragged onto the grid. Manual stamping goes through the normal container
 * click path instead (the carried item is already known server-side).
 */
public record AssemblerPatternCellC2SPacket(int containerId, int index, ItemStack display) {

    public static void encode(AssemblerPatternCellC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeVarInt(packet.index);
        buffer.writeItem(packet.display);
    }

    public static AssemblerPatternCellC2SPacket decode(FriendlyByteBuf buffer) {
        return new AssemblerPatternCellC2SPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readItem());
    }

    public static void handle(AssemblerPatternCellC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            // Only ever touches the assembler the sender currently has open.
            if (sender == null || !(sender.containerMenu instanceof AssemblerMenu menu) || menu.containerId != packet.containerId) {
                return;
            }
            menu.stampPatternCell(packet.index, packet.display);
        });
        context.setPacketHandled(true);
    }
}
