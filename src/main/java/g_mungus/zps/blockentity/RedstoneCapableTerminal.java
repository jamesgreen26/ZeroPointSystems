package g_mungus.zps.blockentity;

import g_mungus.zps.block.cableNetwork.core.NetworkNode;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface RedstoneCapableTerminal {
    int getCurrentSuppliedSignal(int channel);

    default void updateAllSignals(Level level, List<NetworkNode> terminals) {
        AtomicInteger maxSuppliedSignal = new AtomicInteger();
        if (level != null) {
            terminals.forEach(node -> {
                BlockEntity blockEntity = level.getBlockEntity(node.pos());
                if (blockEntity instanceof RedstoneCapableTerminal terminal) {
                    int signal = terminal.getCurrentSuppliedSignal(node.channel());
                    if (signal > maxSuppliedSignal.get()) {
                        maxSuppliedSignal.set(signal);
                    }
                }
            });

            terminals.forEach(node -> {
                BlockEntity blockEntity = level.getBlockEntity(node.pos());
                if (blockEntity instanceof RedstoneConverterBlockEntity) {
                    ((RedstoneConverterBlockEntity) blockEntity).receiveSignal(maxSuppliedSignal.get());
                }
            });
        }
    }
}
