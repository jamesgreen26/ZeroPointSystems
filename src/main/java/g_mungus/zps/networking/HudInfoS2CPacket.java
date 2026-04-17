package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.util.HudInfoProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HudInfoS2CPacket(BlockPos blockPos, CompoundTag info) implements CustomPacketPayload {
    public static final Type<HudInfoS2CPacket> TYPE = new Type<>(ZPSMod.resource("hud_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HudInfoS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HudInfoS2CPacket decode(RegistryFriendlyByteBuf buffer) {
            BlockPos blockPos = buffer.readBlockPos();
            CompoundTag info = buffer.readNbt();
            return new HudInfoS2CPacket(blockPos, info == null ? new CompoundTag() : info);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, HudInfoS2CPacket packet) {
            buffer.writeBlockPos(packet.blockPos());
            buffer.writeNbt(packet.info());
        }
    };

    public static void handle(HudInfoS2CPacket packet, IPayloadContext context) {
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
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyInfo(HudInfoProvider<?> provider, CompoundTag info) {
        HudInfoProvider<T> typedProvider = (HudInfoProvider<T>) provider;
        typedProvider.provideInfo(typedProvider.readInfo(info));
    }

    @Override
    public Type<HudInfoS2CPacket> type() {
        return TYPE;
    }
}
