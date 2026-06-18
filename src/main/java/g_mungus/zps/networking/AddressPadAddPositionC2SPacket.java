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

public record AddressPadAddPositionC2SPacket(InteractionHand hand, BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<AddressPadAddPositionC2SPacket> TYPE = new Type<>(ZPSMod.resource("address_pad_add_position"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AddressPadAddPositionC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AddressPadAddPositionC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new AddressPadAddPositionC2SPacket(
                    buffer.readEnum(InteractionHand.class),
                    buffer.readBlockPos(),
                    buffer.readUtf(64)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, AddressPadAddPositionC2SPacket packet) {
            buffer.writeEnum(packet.hand);
            buffer.writeBlockPos(packet.pos);
            buffer.writeUtf(packet.name, 64);
        }
    };

    public static void handle(AddressPadAddPositionC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            String trimmedName = packet.name().trim();
            if (!AddressPadItem.isValidName(trimmedName)) return;

            ItemStack held = context.player().getItemInHand(packet.hand());
            if (!(held.getItem() instanceof AddressPadItem)) return;

            AddressPadItem.putNamedPosition(held, trimmedName, packet.pos(), true);
        });
    }

    @Override
    public Type<AddressPadAddPositionC2SPacket> type() {
        return TYPE;
    }
}
