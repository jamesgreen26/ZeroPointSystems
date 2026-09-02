package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.gas.CreativeGasGeneratorBlockEntity;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the Creative Gas Generator's screen sends back when any of its controls moves. */
public record CreativeGasGeneratorSettingsC2SPacket(BlockPos blockPos, ResourceLocation gas,
                                                    double rate,
                                                    double temperature) implements CustomPacketPayload {
    public static final Type<CreativeGasGeneratorSettingsC2SPacket> TYPE =
            new Type<>(ZPSMod.resource("creative_gas_generator_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeGasGeneratorSettingsC2SPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CreativeGasGeneratorSettingsC2SPacket decode(RegistryFriendlyByteBuf buffer) {
                    return new CreativeGasGeneratorSettingsC2SPacket(
                            buffer.readBlockPos(),
                            buffer.readResourceLocation(),
                            buffer.readDouble(),
                            buffer.readDouble());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, CreativeGasGeneratorSettingsC2SPacket packet) {
                    buffer.writeBlockPos(packet.blockPos);
                    buffer.writeResourceLocation(packet.gas);
                    buffer.writeDouble(packet.rate);
                    buffer.writeDouble(packet.temperature);
                }
            };

    public static void handle(CreativeGasGeneratorSettingsC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
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
    }

    @Override
    public Type<CreativeGasGeneratorSettingsC2SPacket> type() {
        return TYPE;
    }
}
