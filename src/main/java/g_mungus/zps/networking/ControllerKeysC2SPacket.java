package g_mungus.zps.networking;

import g_mungus.zps.blockentity.ControllerBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

/** What the controller's key screen sends whenever a key changes: all of them, one per channel. */
public record ControllerKeysC2SPacket(BlockPos blockPos, int[] keys) {

    /** GLFW_KEY_LAST; anything above it is not a key. */
    private static final int LAST_KEY = 348;
    private static final int MAX_KEYS = 12;

    public static void encode(ControllerKeysC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeVarIntArray(packet.keys);
    }

    public static ControllerKeysC2SPacket decode(FriendlyByteBuf buffer) {
        return new ControllerKeysC2SPacket(buffer.readBlockPos(), buffer.readVarIntArray(MAX_KEYS));
    }

    public static void handle(ControllerKeysC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
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
        context.setPacketHandled(true);
    }
}
