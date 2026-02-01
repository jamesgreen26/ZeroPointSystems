package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface RedstoneSendingTerminal extends NetworkTerminal {
    int getCurrentSuppliedSignal(int channel);

    default void updateSignal(Level level, int channel) {
        updateAllSignals(level, getTerminals(channel));
    }

    default void updateAllSignals(Level level, List<NetworkNode> terminals) {
        AtomicInteger maxSuppliedSignal = new AtomicInteger();
        if (level != null) {
            terminals.forEach(node -> {
                BlockEntity blockEntity = level.getBlockEntity(node.pos());
                if (blockEntity instanceof RedstoneSendingTerminal terminal) {
                    int signal = terminal.getCurrentSuppliedSignal(node.channel());
                    if (signal > maxSuppliedSignal.get()) {
                        maxSuppliedSignal.set(signal);
                    }
                }
            });

            terminals.forEach(node -> {
                BlockEntity blockEntity = level.getBlockEntity(node.pos());
                if (blockEntity instanceof RedstoneReceivingTerminal receivingTerminal) {
                    receivingTerminal.receiveSignal(maxSuppliedSignal.get(), node.channel());
                }
            });
        }
    }
}
