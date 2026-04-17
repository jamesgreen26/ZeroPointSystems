package g_mungus.zps.networking;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.light_pipe.ScriptComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ScriptComputerC2SPacket(BlockPos computerPos, boolean loop, int delay, String contents) implements CustomPacketPayload {
    public static final Type<ScriptComputerC2SPacket> TYPE = new Type<>(ZPSMod.resource("script_computer_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScriptComputerC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ScriptComputerC2SPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ScriptComputerC2SPacket(
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readUtf()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ScriptComputerC2SPacket packet) {
            buffer.writeBlockPos(packet.computerPos);
            buffer.writeBoolean(packet.loop);
            buffer.writeInt(packet.delay);
            buffer.writeUtf(packet.contents);
        }
    };

    public static void handle(ScriptComputerC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (!level.isLoaded(packet.computerPos)) {
                return;
            }

            BlockEntity blockEntity = level.getBlockEntity(packet.computerPos);
            if (blockEntity instanceof ScriptComputer computer) {
                computer.acceptUpdatePacket(packet);
            }
        });
    }

    @Override
    public Type<ScriptComputerC2SPacket> type() {
        return TYPE;
    }
}
