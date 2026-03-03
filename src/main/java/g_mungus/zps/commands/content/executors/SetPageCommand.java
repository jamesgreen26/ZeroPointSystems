package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.light_pipe.BookHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mod.EventBusSubscriber
public class SetPageCommand {

    private record TaskKey(ServerLevel level, BlockPos pos) {}
    private static final ConcurrentMap<TaskKey, QueuedTask> queue = new ConcurrentHashMap<>();
    private record QueuedTask(int delay, Runnable task) {}

    public static int setPage(ServerLevel serverLevel, BlockPos pos, int page) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (blockEntity instanceof BookHolder) {
            TaskKey key = new TaskKey(serverLevel, pos);

            queue.put(key, new QueuedTask(3, () -> {
                BlockEntity be = serverLevel.getBlockEntity(pos);
                if (be instanceof BookHolder holder && holder.zps$hasBook()) {
                    holder.zps$setPage(page - 1); // 0 based index
                }
            }));
        } else {
            return 0;
        }
        return 1;
    }

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
