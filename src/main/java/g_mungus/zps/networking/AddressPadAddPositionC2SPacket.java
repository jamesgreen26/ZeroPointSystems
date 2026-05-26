package g_mungus.zps.networking;

import g_mungus.zps.item.AddressPadItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AddressPadAddPositionC2SPacket(InteractionHand hand, BlockPos pos, String name) {

    public static void encode(AddressPadAddPositionC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeBlockPos(packet.pos);
        buffer.writeUtf(packet.name, 64);
    }

    public static AddressPadAddPositionC2SPacket decode(FriendlyByteBuf buffer) {
        return new AddressPadAddPositionC2SPacket(
                buffer.readEnum(InteractionHand.class),
                buffer.readBlockPos(),
                buffer.readUtf(64)
        );
    }

    public static void handle(AddressPadAddPositionC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) return;

            String trimmedName = packet.name().trim();
            if (trimmedName.isEmpty()) return;

            ItemStack held = context.getSender().getItemInHand(packet.hand());
            if (!(held.getItem() instanceof AddressPadItem)) return;

            AddressPadItem.putNamedPosition(held, trimmedName, packet.pos());
        });
        context.setPacketHandled(true);
    }
}
