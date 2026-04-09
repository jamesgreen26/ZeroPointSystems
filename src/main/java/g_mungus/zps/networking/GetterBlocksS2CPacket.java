package g_mungus.zps.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public record GetterBlocksS2CPacket(List<ResourceLocation> associatedBlocks) {

    public static final Set<ResourceLocation> getter_capable_blocks = ConcurrentHashMap.newKeySet();

    public static void encode(GetterBlocksS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.associatedBlocks, FriendlyByteBuf::writeResourceLocation);
    }

    public static GetterBlocksS2CPacket decode(FriendlyByteBuf buffer) {
        return new GetterBlocksS2CPacket(buffer.readList(FriendlyByteBuf::readResourceLocation));
    }

    public static void handle(GetterBlocksS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> getter_capable_blocks.addAll(packet.associatedBlocks()));
        context.setPacketHandled(true);
    }
}
