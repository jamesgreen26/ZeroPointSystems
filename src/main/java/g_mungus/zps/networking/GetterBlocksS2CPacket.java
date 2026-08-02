package g_mungus.zps.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public record GetterBlocksS2CPacket(Map<ResourceLocation, List<String>> getterNamesByBlock) {

    public static final Set<ResourceLocation> getter_capable_blocks = ConcurrentHashMap.newKeySet();
    public static final Map<ResourceLocation, Set<String>> getter_names_by_block = new ConcurrentHashMap<>();

    public static void encode(GetterBlocksS2CPacket packet, FriendlyByteBuf buffer) {
        writeGetterNamesByBlock(buffer, packet.getterNamesByBlock());
    }

    public static GetterBlocksS2CPacket decode(FriendlyByteBuf buffer) {
        return new GetterBlocksS2CPacket(readGetterNamesByBlock(buffer));
    }

    public static void handle(GetterBlocksS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            getter_capable_blocks.clear();
            getter_names_by_block.clear();

            packet.getterNamesByBlock().forEach((block, names) -> {
                getter_capable_blocks.add(block);
                getter_names_by_block.computeIfAbsent(block, ignored -> ConcurrentHashMap.newKeySet())
                        .addAll(names);
            });
        });
        context.setPacketHandled(true);
    }

    private static Map<ResourceLocation, List<String>> readGetterNamesByBlock(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, List<String>> namesByBlock = new HashMap<>();

        for (int i = 0; i < size; i++) {
            ResourceLocation block = buffer.readResourceLocation();
            List<String> names = buffer.readList(buf -> buf.readUtf());
            namesByBlock.put(block, names);
        }

        return namesByBlock;
    }

    private static void writeGetterNamesByBlock(FriendlyByteBuf buffer, Map<ResourceLocation, List<String>> namesByBlock) {
        buffer.writeVarInt(namesByBlock.size());
        namesByBlock.forEach((block, names) -> {
            buffer.writeResourceLocation(block);
            buffer.writeCollection(names, (buf, name) -> buf.writeUtf(name));
        });
    }
}
