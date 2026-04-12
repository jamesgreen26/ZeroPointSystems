package g_mungus.zps.commands.content.executors;

import g_mungus.zps.blockentity.light_pipe.BookHolder;
import g_mungus.zps.util.BookPageTextLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
                    CompoundTag tag = book.getOrCreateTag();
                    if (!tag.contains("pages", Tag.TAG_LIST)) {
                        tag.put("pages", new ListTag());
                    }
                    ListTag pages = tag.getList("pages", 8);
                    while (pages.size() <= page) {
                        pages.add(StringTag.valueOf(""));
                        holder.zps$onPageAdded();
                    }
                    String normalizedText = text.replace("\\n", "\n");
                    pages.set(page, StringTag.valueOf(BookPageTextLimiter.truncateToDisplayableLength(normalizedText)));
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
