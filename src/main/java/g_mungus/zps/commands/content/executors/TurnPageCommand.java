package g_mungus.zps.commands.content.executors;

import com.mojang.brigadier.tree.CommandNode;
import g_mungus.zps.blockentity.light_pipe.BookHolder;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.commands.api_impl.exceptions.UnsupportedOperationException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mod.EventBusSubscriber
public class TurnPageCommand {

    private record TaskKey(ServerLevel level, BlockPos pos) {}
    private static final ConcurrentMap<TaskKey, QueuedTask> queue = new ConcurrentHashMap<>();
    private record QueuedTask(int delay, Runnable task) {}

    public static final CommandNode<CommandSourceStack> COMMAND = Commands.literal("TURN_PAGE")
                    .executes(context -> {
                        BlockPos pos = ZPSCommands.getPosition(context);
                        ServerLevel serverLevel = context.getSource().getLevel();
                        Block block = serverLevel.getBlockState(pos).getBlock();

                        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
                        if (blockEntity instanceof BookHolder) {
                            TaskKey key = new TaskKey(serverLevel, pos);

                            queue.put(key, new QueuedTask(3, () -> {
                                BlockEntity be = serverLevel.getBlockEntity(pos);
                                if (be instanceof BookHolder holder && holder.zps$hasBook()) {
                                    holder.zps$cyclePages();
                                }
                            }));
                        } else {
                            throw UnsupportedOperationException.build("TURN_PAGE", block);
                        }
                        return 1;
                    }).build();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        queue.forEach((key, queued) -> {
            if (queued.delay <= 0) {
                if (queue.remove(key, queued)) {
                    queued.task.run();
                }
            } else {
                queue.computeIfPresent(key, (k, q) ->
                        new QueuedTask(q.delay - 1, q.task)
                );
            }
        });
    }

}
