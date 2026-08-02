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

public record ExecutorBlocksS2CPacket(Map<ResourceLocation, List<String>> executorNamesByBlock) {
    public static final Set<ResourceLocation> command_capable_blocks = ConcurrentHashMap.newKeySet();
    public static final Map<ResourceLocation, Set<String>> command_names_by_block = new ConcurrentHashMap<>();

    public static void encode(ExecutorBlocksS2CPacket packet, FriendlyByteBuf buffer) {
        writeExecutorNamesByBlock(buffer, packet.executorNamesByBlock());
    }

    public static ExecutorBlocksS2CPacket decode(FriendlyByteBuf buffer) {
        return new ExecutorBlocksS2CPacket(readExecutorNamesByBlock(buffer));
    }

    public static void handle(ExecutorBlocksS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            command_capable_blocks.clear();
            command_names_by_block.clear();

            packet.executorNamesByBlock().forEach((block, names) -> {
                command_capable_blocks.add(block);
                command_names_by_block.computeIfAbsent(block, ignored -> ConcurrentHashMap.newKeySet())
                        .addAll(names);
            });
        });
        context.setPacketHandled(true);
    }

    private static Map<ResourceLocation, List<String>> readExecutorNamesByBlock(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, List<String>> namesByBlock = new HashMap<>();

        for (int i = 0; i < size; i++) {
            ResourceLocation block = buffer.readResourceLocation();
            List<String> names = buffer.readList(buf -> buf.readUtf());
            namesByBlock.put(block, names);
        }

        return namesByBlock;
    }

    private static void writeExecutorNamesByBlock(FriendlyByteBuf buffer, Map<ResourceLocation, List<String>> namesByBlock) {
        buffer.writeVarInt(namesByBlock.size());
        namesByBlock.forEach((block, names) -> {
            buffer.writeResourceLocation(block);
            buffer.writeCollection(names, (buf, name) -> buf.writeUtf(name));
        });
    }
}
