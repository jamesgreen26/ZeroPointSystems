package g_mungus.zps.networking;

import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** What the Creative Gas Generator's screen sends back when any of its controls moves. */
public record CreativeGasGeneratorSettingsC2SPacket(BlockPos blockPos, ResourceLocation gas, double rate, double temperature) {

    public static void encode(CreativeGasGeneratorSettingsC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeResourceLocation(packet.gas);
        buffer.writeDouble(packet.rate);
        buffer.writeDouble(packet.temperature);
    }

    public static CreativeGasGeneratorSettingsC2SPacket decode(FriendlyByteBuf buffer) {
        return new CreativeGasGeneratorSettingsC2SPacket(
                buffer.readBlockPos(),
                buffer.readResourceLocation(),
                buffer.readDouble(),
                buffer.readDouble());
    }

    public static void handle(CreativeGasGeneratorSettingsC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ServerLevel level = sender.serverLevel();
            // A creative-only block: nobody in survival should be able to open a gas tap.
            if (!sender.isCreative() && !sender.hasPermissions(2)) {
                return;
            }
            if (!(level.getBlockEntity(packet.blockPos) instanceof CreativeGasGeneratorBlockEntity generator)) {
                return;
            }
            // Ship-aware, so a generator riding a Valkyrien Skies ship measures the right distance.
            Vec3 worldCenter = Compat.toWorldPos(level, Vec3.atCenterOf(packet.blockPos));
            if (worldCenter.distanceToSqr(sender.position()) > 64.0D) {
                return;
            }
            generator.setSettings(packet.gas, packet.rate, packet.temperature);
        });
        context.setPacketHandled(true);
    }
}
