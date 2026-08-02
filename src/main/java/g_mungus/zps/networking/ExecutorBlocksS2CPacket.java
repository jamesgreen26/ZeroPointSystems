package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record ExecutorBlocksS2CPacket(Map<ResourceLocation, List<String>> executorNamesByBlock) implements CustomPacketPayload {
    public static final Type<ExecutorBlocksS2CPacket> TYPE = new Type<>(ZPSMod.resource("executor_blocks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExecutorBlocksS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ExecutorBlocksS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ExecutorBlocksS2CPacket(readExecutorNamesByBlock(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ExecutorBlocksS2CPacket packet) {
            writeExecutorNamesByBlock(buffer, packet.executorNamesByBlock());
        }
    };

    public static final Set<ResourceLocation> command_capable_blocks = ConcurrentHashMap.newKeySet();
    public static final Map<ResourceLocation, Set<String>> command_names_by_block = new ConcurrentHashMap<>();

    public static void handle(ExecutorBlocksS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            command_capable_blocks.clear();
            command_names_by_block.clear();

            packet.executorNamesByBlock().forEach((block, names) -> {
                command_capable_blocks.add(block);
                command_names_by_block.computeIfAbsent(block, ignored -> ConcurrentHashMap.newKeySet())
                        .addAll(names);
            });
        });
    }

    @Override
    public Type<ExecutorBlocksS2CPacket> type() {
        return TYPE;
    }

    private static Map<ResourceLocation, List<String>> readExecutorNamesByBlock(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, List<String>> namesByBlock = new HashMap<>();

        for (int i = 0; i < size; i++) {
            ResourceLocation block = buffer.readResourceLocation();
            List<String> names = buffer.readList(buf -> buf.readUtf());
            namesByBlock.put(block, names);
        }

        return namesByBlock;
    }

    private static void writeExecutorNamesByBlock(RegistryFriendlyByteBuf buffer, Map<ResourceLocation, List<String>> namesByBlock) {
        buffer.writeVarInt(namesByBlock.size());
        namesByBlock.forEach((block, names) -> {
            buffer.writeResourceLocation(block);
            buffer.writeCollection(new ArrayList<>(names), (buf, name) -> buf.writeUtf(name));
        });
    }
}
