package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.AddressPadItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record AddressPadSetEntriesC2SPacket(InteractionHand hand, List<AddressPadItem.Entry> entries) implements CustomPacketPayload {
    private static final int MAX_NAME_LENGTH = 64;

    public static final Type<AddressPadSetEntriesC2SPacket> TYPE = new Type<>(ZPSMod.resource("address_pad_set_entries"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddressPadSetEntriesC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AddressPadSetEntriesC2SPacket decode(RegistryFriendlyByteBuf buffer) {
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

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, AddressPadSetEntriesC2SPacket packet) {
            buffer.writeEnum(packet.hand);
            int count = Math.min(packet.entries.size(), AddressPadItem.MAX_ENTRIES);
            buffer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                AddressPadItem.Entry entry = packet.entries.get(i);
                buffer.writeUtf(entry.name(), MAX_NAME_LENGTH);
                buffer.writeBlockPos(entry.pos());
            }
        }
    };

    public static void handle(AddressPadSetEntriesC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack held = context.player().getItemInHand(packet.hand());
            if (!(held.getItem() instanceof AddressPadItem)) return;
            AddressPadItem.replaceNamedPositions(held, packet.entries());
        });
    }

    @Override
    public Type<AddressPadSetEntriesC2SPacket> type() {
        return TYPE;
    }
}
