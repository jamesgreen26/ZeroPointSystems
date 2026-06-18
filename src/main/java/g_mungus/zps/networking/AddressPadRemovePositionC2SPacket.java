package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.AddressPadItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AddressPadRemovePositionC2SPacket(InteractionHand hand, String name) implements CustomPacketPayload {
    public static final Type<AddressPadRemovePositionC2SPacket> TYPE = new Type<>(ZPSMod.resource("address_pad_remove_position"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddressPadRemovePositionC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AddressPadRemovePositionC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new AddressPadRemovePositionC2SPacket(
                    buffer.readEnum(InteractionHand.class),
                    buffer.readUtf(64)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, AddressPadRemovePositionC2SPacket packet) {
            buffer.writeEnum(packet.hand);
            buffer.writeUtf(packet.name, 64);
        }
    };

    public static void handle(AddressPadRemovePositionC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            String trimmedName = packet.name().trim();
            if (trimmedName.isEmpty()) return;

            ItemStack held = context.player().getItemInHand(packet.hand());
            if (!(held.getItem() instanceof AddressPadItem)) return;

            AddressPadItem.removeNamedPosition(held, trimmedName);
        });
    }

    @Override
    public Type<AddressPadRemovePositionC2SPacket> type() {
        return TYPE;
    }
}
