package g_mungus.zps.networking;

import g_mungus.zps.util.HudInfoProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record RequestHudInfoC2SPacket(BlockPos blockPos) {

    public static void encode(RequestHudInfoC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos());
    }

    public static RequestHudInfoC2SPacket decode(FriendlyByteBuf buffer) {
        return new RequestHudInfoC2SPacket(buffer.readBlockPos());
    }

    public static void handle(RequestHudInfoC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) {
                return;
            }

            Level level = context.getSender().level();
            if (!level.isLoaded(packet.blockPos())) {
                return;
            }

            BlockEntity blockEntity = level.getBlockEntity(packet.blockPos());
            if (blockEntity instanceof HudInfoProvider<?> provider) {
                ZPSGamePackets.INSTANCE.send(
                        PacketDistributor.PLAYER.with(context::getSender),
                        new HudInfoS2CPacket(packet.blockPos(), writeInfo(provider))
                );
            }
        });
        context.setPacketHandled(true);
    }

    @SuppressWarnings("unchecked")
    private static <T> CompoundTag writeInfo(HudInfoProvider<?> provider) {
        HudInfoProvider<T> typedProvider = (HudInfoProvider<T>) provider;
        return typedProvider.writeInfo(typedProvider.getInfo());
    }
}
