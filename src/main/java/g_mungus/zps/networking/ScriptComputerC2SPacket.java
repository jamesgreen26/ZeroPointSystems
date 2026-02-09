package g_mungus.zps.networking;

import g_mungus.zps.blockentity.light_pipe.ScriptComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ScriptComputerC2SPacket(BlockPos computerPos, boolean loop, String contents) {

    public static void encode(ScriptComputerC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.computerPos);
        buffer.writeBoolean(packet.loop);
        buffer.writeUtf(packet.contents);
    }

    public static ScriptComputerC2SPacket decode(FriendlyByteBuf buffer) {
        return new ScriptComputerC2SPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readUtf()
        );
    }

    public static void handle(ScriptComputerC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                Level level = context.getSender().level();
                if (!level.isLoaded(packet.computerPos)) return;
                BlockEntity blockEntity = level.getBlockEntity(packet.computerPos);
                if (blockEntity instanceof ScriptComputer computer) {
                    computer.acceptUpdatePacket(packet);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
