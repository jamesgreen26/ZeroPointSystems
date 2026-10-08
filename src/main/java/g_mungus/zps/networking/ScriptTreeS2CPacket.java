package g_mungus.zps.networking;

import g_mungus.zps.client.script.ClientScripts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything a client needs to write scripts, sent whole whenever the server builds its scripts:
 * the script tree as {@link g_mungus.munguscript.engine.codec.ScriptTreeCodec} encodes it, with
 * which blocks each node is for, and the "works with" lists the ponder index shows.
 *
 * @param tree             the encoded script tree
 * @param executorsByBlock the executors each block that has an item works with
 * @param gettersByBlock   the getters each block that has an item can be read with
 */
public record ScriptTreeS2CPacket(byte[] tree, Map<ResourceLocation, List<String>> executorsByBlock,
                                  Map<ResourceLocation, List<String>> gettersByBlock) {

    public static void encode(ScriptTreeS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByteArray(packet.tree);
        writeNames(buffer, packet.executorsByBlock);
        writeNames(buffer, packet.gettersByBlock);
    }

    public static ScriptTreeS2CPacket decode(FriendlyByteBuf buffer) {
        return new ScriptTreeS2CPacket(buffer.readByteArray(), readNames(buffer), readNames(buffer));
    }

    public static void handle(ScriptTreeS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientScripts.receive(packet.tree, packet.executorsByBlock, packet.gettersByBlock));
        context.setPacketHandled(true);
    }

    private static Map<ResourceLocation, List<String>> readNames(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, List<String>> names = new HashMap<>();
        for (int i = 0; i < size; i++) {
            names.put(buffer.readResourceLocation(), buffer.readList(FriendlyByteBuf::readUtf));
        }
        return names;
    }

    private static void writeNames(FriendlyByteBuf buffer, Map<ResourceLocation, List<String>> names) {
        buffer.writeVarInt(names.size());
        names.forEach((block, list) -> {
            buffer.writeResourceLocation(block);
            buffer.writeCollection(list, FriendlyByteBuf::writeUtf);
        });
    }
}
