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

public record ExecutorBlocksS2CPacket(List<ResourceLocation> associatedBlocks) implements CustomPacketPayload {
    public static final Type<ExecutorBlocksS2CPacket> TYPE = new Type<>(ZPSMod.resource("executor_blocks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExecutorBlocksS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ExecutorBlocksS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ExecutorBlocksS2CPacket(buffer.readList(buf -> buf.readResourceLocation()));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ExecutorBlocksS2CPacket packet) {
            buffer.writeCollection(packet.associatedBlocks, (buf, id) -> buf.writeResourceLocation(id));
        }
    };

    public static final Set<ResourceLocation> command_capable_blocks = ConcurrentHashMap.newKeySet();
    private static final List<ResourceLocation> blacklist = List.of(
            ResourceLocation.parse("create:pink_valve_handle"),
            ResourceLocation.parse("create:magenta_valve_handle"),
            ResourceLocation.parse("create:white_valve_handle"),
            ResourceLocation.parse("create:cyan_valve_handle"),
            ResourceLocation.parse("create:light_blue_valve_handle"),
            ResourceLocation.parse("create:lime_valve_handle"),
            ResourceLocation.parse("create:purple_valve_handle"),
            ResourceLocation.parse("create:red_valve_handle"),
            ResourceLocation.parse("create:gray_valve_handle"),
            ResourceLocation.parse("create:light_gray_valve_handle"),
            ResourceLocation.parse("create:orange_valve_handle"),
            ResourceLocation.parse("create:blue_valve_handle"),
            ResourceLocation.parse("create:black_valve_handle"),
            ResourceLocation.parse("create:yellow_valve_handle"),
            ResourceLocation.parse("create:brown_valve_handle"),
            ResourceLocation.parse("create:green_valve_handle")
    );

    public static void handle(ExecutorBlocksS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            command_capable_blocks.addAll(packet.associatedBlocks());
            blacklist.forEach(command_capable_blocks::remove);
        });
    }

    @Override
    public Type<ExecutorBlocksS2CPacket> type() {
        return TYPE;
    }
}
