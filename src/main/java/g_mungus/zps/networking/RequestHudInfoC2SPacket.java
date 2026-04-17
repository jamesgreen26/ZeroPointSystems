package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.util.HudInfoProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestHudInfoC2SPacket(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<RequestHudInfoC2SPacket> TYPE = new Type<>(ZPSMod.resource("request_hud_info"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestHudInfoC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestHudInfoC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new RequestHudInfoC2SPacket(buffer.readBlockPos());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RequestHudInfoC2SPacket packet) {
            buffer.writeBlockPos(packet.blockPos());
        }
    };

    public static void handle(RequestHudInfoC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (!level.isLoaded(packet.blockPos())) {
                return;
            }

            BlockEntity blockEntity = level.getBlockEntity(packet.blockPos());
            if (blockEntity instanceof HudInfoProvider<?> provider) {
                PacketDistributor.sendToPlayer((ServerPlayer) context.player(), new HudInfoS2CPacket(packet.blockPos(), writeInfo(provider)));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> CompoundTag writeInfo(HudInfoProvider<?> provider) {
        HudInfoProvider<T> typedProvider = (HudInfoProvider<T>) provider;
        return typedProvider.writeInfo(typedProvider.getInfo());
    }

    @Override
    public Type<RequestHudInfoC2SPacket> type() {
        return TYPE;
    }
}
