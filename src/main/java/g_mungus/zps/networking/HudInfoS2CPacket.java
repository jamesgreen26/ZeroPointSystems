package g_mungus.zps.networking;

import g_mungus.zps.util.HudInfoProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HudInfoS2CPacket(BlockPos blockPos, CompoundTag info) {

    public static void encode(HudInfoS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos());
        buffer.writeNbt(packet.info());
    }

    public static HudInfoS2CPacket decode(FriendlyByteBuf buffer) {
        BlockPos blockPos = buffer.readBlockPos();
        CompoundTag info = buffer.readNbt();
        return new HudInfoS2CPacket(blockPos, info == null ? new CompoundTag() : info);
    }

    public static void handle(HudInfoS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || !minecraft.level.isLoaded(packet.blockPos())) {
                return;
            }

            BlockEntity blockEntity = minecraft.level.getBlockEntity(packet.blockPos());
            if (blockEntity instanceof HudInfoProvider<?> provider) {
                applyInfo(provider, packet.info());
            }
        });
        context.setPacketHandled(true);
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyInfo(HudInfoProvider<?> provider, CompoundTag info) {
        HudInfoProvider<T> typedProvider = (HudInfoProvider<T>) provider;
        typedProvider.provideInfo(typedProvider.readInfo(info));
    }
}
