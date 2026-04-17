package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record GetterBlocksS2CPacket(List<ResourceLocation> associatedBlocks) implements CustomPacketPayload {
    public static final Type<GetterBlocksS2CPacket> TYPE = new Type<>(ZPSMod.resource("getter_blocks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GetterBlocksS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GetterBlocksS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new GetterBlocksS2CPacket(buffer.readList(buf -> buf.readResourceLocation()));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, GetterBlocksS2CPacket packet) {
            buffer.writeCollection(packet.associatedBlocks, (buf, id) -> buf.writeResourceLocation(id));
        }
    };

    public static final Set<ResourceLocation> getter_capable_blocks = ConcurrentHashMap.newKeySet();

    public static void handle(GetterBlocksS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> getter_capable_blocks.addAll(packet.associatedBlocks()));
    }

    @Override
    public Type<GetterBlocksS2CPacket> type() {
        return TYPE;
    }
}
