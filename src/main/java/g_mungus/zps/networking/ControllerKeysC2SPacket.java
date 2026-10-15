package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.ControllerBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;

/** What the controller's key screen sends whenever a key changes: all of them, one per channel. */
public record ControllerKeysC2SPacket(BlockPos blockPos, int[] keys) implements CustomPacketPayload {
    public static final Type<ControllerKeysC2SPacket> TYPE = new Type<>(ZPSMod.resource("controller_keys"));

    /** GLFW_KEY_LAST; anything above it is not a key. */
    private static final int LAST_KEY = 348;
    private static final int MAX_KEYS = 12;

    public static final StreamCodec<RegistryFriendlyByteBuf, ControllerKeysC2SPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControllerKeysC2SPacket::blockPos,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_KEYS)).map(
                    list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> Arrays.stream(array).boxed().toList()),
            ControllerKeysC2SPacket::keys,
            ControllerKeysC2SPacket::new);

    public static void handle(ControllerKeysC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof ControllerBlockEntity<?> controller)) {
                return;
            }
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            int[] keys = Arrays.stream(packet.keys)
                    .map(key -> key < 0 || key > LAST_KEY ? ControllerBlockEntity.UNBOUND : key)
                    .toArray();
            controller.setKeys(keys);
        });
    }

    @Override
    public Type<ControllerKeysC2SPacket> type() {
        return TYPE;
    }
}
