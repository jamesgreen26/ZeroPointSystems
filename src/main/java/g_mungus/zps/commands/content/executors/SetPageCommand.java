package g_mungus.zps.commands.content.executors;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.light_pipe.BookHolder;
import g_mungus.zps.util.BookComponents;
import g_mungus.zps.util.BookPageTextLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@EventBusSubscriber(modid = ZPSMod.MOD_ID)
public class SetPageCommand {

    private record TaskKey(ServerLevel level, BlockPos pos) {}
    private static final ConcurrentMap<TaskKey, QueuedTask> queue = new ConcurrentHashMap<>();
    private record QueuedTask(int delay, Runnable task) {}

    public static int writeToCurrentPage(ServerLevel serverLevel, BlockPos pos, String text) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (blockEntity instanceof BookHolder) {
            TaskKey key = new TaskKey(serverLevel, pos);

            queue.put(key, new QueuedTask(0, () -> {
                BlockEntity be = serverLevel.getBlockEntity(pos);
                if (be instanceof BookHolder holder && holder.zps$hasBook()) {
                    ItemStack book = holder.zps$getBook();
                    if (book == null) return;
                    int page = holder.zps$getCurrentPage();
                    if (page < 0) page = 0;
                    while (BookComponents.getPageCount(book) <= page) {
                        BookComponents.setWritablePage(book, BookComponents.getPageCount(book), "");
                        holder.zps$onPageAdded();
                    }
                    String normalizedText = text.replace("\\n", "\n");
                    BookComponents.setWritablePage(book, page, BookPageTextLimiter.truncateToDisplayableLength(normalizedText));
                    holder.zps$onPageWritten();
                }
            }));
        } else {
            return 0;
        }
        return 1;
    }

    public static int setPage(ServerLevel serverLevel, BlockPos pos, int page) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (blockEntity instanceof BookHolder) {
            TaskKey key = new TaskKey(serverLevel, pos);

            queue.put(key, new QueuedTask(0, () -> {
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
    public static void onServerTick(ServerTickEvent.Pre event) {
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
