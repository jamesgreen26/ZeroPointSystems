package g_mungus.zps.networking;

import g_mungus.zps.block.reactor.ReactorPortMode;
import g_mungus.zps.blockentity.reactor.ReactorPortBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.gas.GasFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What the reactor port's screen sends back on Done: the direction and the gas filter. */
public record ReactorPortSettingsC2SPacket(BlockPos blockPos, ReactorPortMode mode, GasFilter filter) {

    public static void encode(ReactorPortSettingsC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeEnum(packet.mode);
        packet.filter.write(buffer);
    }

    public static ReactorPortSettingsC2SPacket decode(FriendlyByteBuf buffer) {
        return new ReactorPortSettingsC2SPacket(
                buffer.readBlockPos(),
                buffer.readEnum(ReactorPortMode.class),
                GasFilter.read(buffer));
    }

    public static void handle(ReactorPortSettingsC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ServerLevel level = sender.serverLevel();
            if (!(level.getBlockEntity(packet.blockPos) instanceof ReactorPortBlockEntity port)) {
                return;
            }
            // Ship-aware, so a port riding a Valkyrien Skies ship measures the right distance.
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            port.setSettings(packet.mode, packet.filter);
        });
        context.setPacketHandled(true);
    }
}
