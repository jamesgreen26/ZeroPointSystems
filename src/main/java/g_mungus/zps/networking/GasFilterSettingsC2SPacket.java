package g_mungus.zps.networking;

import g_mungus.zps.blockentity.gas.GasFilterBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.gas.GasFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What the gas filter's screen sends on every toggle: the whole list of blocked gases. */
public record GasFilterSettingsC2SPacket(BlockPos blockPos, GasFilter filter) {

    public static void encode(GasFilterSettingsC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos);
        packet.filter.write(buffer);
    }

    public static GasFilterSettingsC2SPacket decode(FriendlyByteBuf buffer) {
        return new GasFilterSettingsC2SPacket(buffer.readBlockPos(), GasFilter.read(buffer));
    }

    public static void handle(GasFilterSettingsC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof GasFilterBlockEntity filter)) {
                return;
            }
            // Ship-aware, so a filter riding a Valkyrien Skies ship measures the right distance.
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            filter.setFilter(packet.filter);
        });
        context.setPacketHandled(true);
    }
}
