package g_mungus.zps.networking;

import g_mungus.zps.item.AddressPadItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AddressPadRemovePositionC2SPacket(InteractionHand hand, String name) {
    public static void encode(AddressPadRemovePositionC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeUtf(packet.name, 64);
    }

    public static AddressPadRemovePositionC2SPacket decode(FriendlyByteBuf buffer) {
        return new AddressPadRemovePositionC2SPacket(
                buffer.readEnum(InteractionHand.class),
                buffer.readUtf(64)
        );
    }

    public static void handle(AddressPadRemovePositionC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) return;

            String trimmedName = packet.name().trim();
            if (trimmedName.isEmpty()) return;

            ItemStack held = context.getSender().getItemInHand(packet.hand());
            if (!(held.getItem() instanceof AddressPadItem)) return;

            AddressPadItem.removeNamedPosition(held, trimmedName);
        });
        context.setPacketHandled(true);
    }
}
