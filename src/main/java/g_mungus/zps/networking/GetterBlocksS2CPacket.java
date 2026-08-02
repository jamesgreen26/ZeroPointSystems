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

public record GetterBlocksS2CPacket(Map<ResourceLocation, List<String>> getterNamesByBlock) implements CustomPacketPayload {
    public static final Type<GetterBlocksS2CPacket> TYPE = new Type<>(ZPSMod.resource("getter_blocks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GetterBlocksS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GetterBlocksS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new GetterBlocksS2CPacket(readGetterNamesByBlock(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, GetterBlocksS2CPacket packet) {
            writeGetterNamesByBlock(buffer, packet.getterNamesByBlock());
        }
    };

    public static final Set<ResourceLocation> getter_capable_blocks = ConcurrentHashMap.newKeySet();
    public static final Map<ResourceLocation, Set<String>> getter_names_by_block = new ConcurrentHashMap<>();

    public static void handle(GetterBlocksS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            getter_capable_blocks.clear();
            getter_names_by_block.clear();

            packet.getterNamesByBlock().forEach((block, names) -> {
                getter_capable_blocks.add(block);
                getter_names_by_block.computeIfAbsent(block, ignored -> ConcurrentHashMap.newKeySet())
                        .addAll(names);
            });
        });
    }

    @Override
    public Type<GetterBlocksS2CPacket> type() {
        return TYPE;
    }

    private static Map<ResourceLocation, List<String>> readGetterNamesByBlock(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, List<String>> namesByBlock = new HashMap<>();

        for (int i = 0; i < size; i++) {
            ResourceLocation block = buffer.readResourceLocation();
            List<String> names = buffer.readList(buf -> buf.readUtf());
            namesByBlock.put(block, names);
        }

        return namesByBlock;
    }

    private static void writeGetterNamesByBlock(RegistryFriendlyByteBuf buffer, Map<ResourceLocation, List<String>> namesByBlock) {
        buffer.writeVarInt(namesByBlock.size());
        namesByBlock.forEach((block, names) -> {
            buffer.writeResourceLocation(block);
            buffer.writeCollection(new ArrayList<>(names), (buf, name) -> buf.writeUtf(name));
        });
    }
}
