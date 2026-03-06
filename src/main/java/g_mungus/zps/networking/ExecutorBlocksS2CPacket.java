package g_mungus.zps.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public record ExecutorBlocksS2CPacket(List<ResourceLocation> associatedBlocks) {

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

    public static void encode(ExecutorBlocksS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.associatedBlocks, FriendlyByteBuf::writeResourceLocation);
    }

    public static ExecutorBlocksS2CPacket decode(FriendlyByteBuf buffer) {
        return new ExecutorBlocksS2CPacket(buffer.readList(FriendlyByteBuf::readResourceLocation));
    }

    public static void handle(ExecutorBlocksS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            command_capable_blocks.addAll(packet.associatedBlocks());
            blacklist.forEach(command_capable_blocks::remove);
        });
        context.setPacketHandled(true);
    }
}
