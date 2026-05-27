package g_mungus.zps.networking;

import g_mungus.zps.item.AddressPadItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record AddressPadSetEntriesC2SPacket(InteractionHand hand, List<AddressPadItem.Entry> entries) {
    private static final int MAX_NAME_LENGTH = 64;

    public static void encode(AddressPadSetEntriesC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        int count = Math.min(packet.entries.size(), AddressPadItem.MAX_ENTRIES);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            AddressPadItem.Entry entry = packet.entries.get(i);
            buffer.writeUtf(entry.name(), MAX_NAME_LENGTH);
            buffer.writeBlockPos(entry.pos());
        }
    }

    public static AddressPadSetEntriesC2SPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        int count = Math.max(0, Math.min(buffer.readVarInt(), AddressPadItem.MAX_ENTRIES));
        List<AddressPadItem.Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buffer.readUtf(MAX_NAME_LENGTH).trim();
            BlockPos pos = buffer.readBlockPos();
            entries.add(new AddressPadItem.Entry(name, pos));
        }
        return new AddressPadSetEntriesC2SPacket(hand, entries);
    }

    public static void handle(AddressPadSetEntriesC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) return;
            ItemStack held = context.getSender().getItemInHand(packet.hand());
            if (!(held.getItem() instanceof AddressPadItem)) return;
            AddressPadItem.replaceNamedPositions(held, packet.entries());
        });
        context.setPacketHandled(true);
    }
}
