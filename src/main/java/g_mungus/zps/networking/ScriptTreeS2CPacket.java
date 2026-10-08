package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.script.ClientScripts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                                  Map<ResourceLocation, List<String>> gettersByBlock) implements CustomPacketPayload {
    public static final Type<ScriptTreeS2CPacket> TYPE = new Type<>(ZPSMod.resource("script_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScriptTreeS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ScriptTreeS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ScriptTreeS2CPacket(buffer.readByteArray(), readNames(buffer), readNames(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ScriptTreeS2CPacket packet) {
            buffer.writeByteArray(packet.tree);
            writeNames(buffer, packet.executorsByBlock);
            writeNames(buffer, packet.gettersByBlock);
        }
    };

    public static void handle(ScriptTreeS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientScripts.receive(packet.tree, packet.executorsByBlock, packet.gettersByBlock));
    }

    @Override
    public Type<ScriptTreeS2CPacket> type() {
        return TYPE;
    }

    private static Map<ResourceLocation, List<String>> readNames(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, List<String>> names = new HashMap<>();
        for (int i = 0; i < size; i++) {
            names.put(buffer.readResourceLocation(), buffer.readList(buf -> buf.readUtf()));
        }
        return names;
    }

    private static void writeNames(RegistryFriendlyByteBuf buffer, Map<ResourceLocation, List<String>> names) {
        buffer.writeVarInt(names.size());
        names.forEach((block, list) -> {
            buffer.writeResourceLocation(block);
            buffer.writeCollection(list, (buf, name) -> buf.writeUtf(name));
        });
    }
}
