package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.menu.AssemblerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Stamps one cell of an open assembler's ghost pattern with an item the player never held — currently
 * sent when a JEI ingredient is dragged onto the grid. Manual stamping goes through the normal container
 * click path instead (the carried item is already known server-side).
 */
public record AssemblerPatternCellC2SPacket(int containerId, int index, ItemStack display) implements CustomPacketPayload {
    public static final Type<AssemblerPatternCellC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("assembler_pattern_cell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerPatternCellC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AssemblerPatternCellC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new AssemblerPatternCellC2SPacket(
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, AssemblerPatternCellC2SPacket packet) {
                    buffer.writeVarInt(packet.containerId);
                    buffer.writeVarInt(packet.index);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.display);
                }
            };

    public static void handle(AssemblerPatternCellC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            // Only ever touches the assembler the sender currently has open.
            if (!(sender.containerMenu instanceof AssemblerMenu menu) || menu.containerId != packet.containerId) {
                return;
            }
            menu.stampPatternCell(packet.index, packet.display);
        });
    }

    @Override
    public Type<AssemblerPatternCellC2SPacket> type() {
        return TYPE;
    }
}
