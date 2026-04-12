package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.light_pipe.DataTranscriberBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.util.BookPageTextLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class DataTranscriberBlockEntity extends AbstractTextDataReceiver {
    public DataTranscriberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_TRANSCRIBER.get(), pos, state);
    }

    private long lastPrintTime = -20L;
    private String lastWrittenText = null;

    public void tick() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(DataTranscriberBlock.POWERED) || !state.getValue(DataTranscriberBlock.POWERED)) {
            lastPrintTime = -20L;
            return;
        }
        long time = level.getGameTime();
        if (lastPrintTime + 10 < time) {
            boolean shouldPlayEffects = writeText();
            lastPrintTime = time;
            if (shouldPlayEffects) {
                level.playSound(null, getBlockPos(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 1f, 1f);
            }
        }
    }

    public boolean writeText() {
        if (level instanceof ServerLevel serverLevel) {
            BlockEntity belowBE = serverLevel.getBlockEntity(getBlockPos().below());
            BookHolder bookHolder;
            if (belowBE instanceof BookHolder holder) {
                bookHolder = holder;
            } else if (belowBE instanceof LecternBlockEntity lectern) {
                bookHolder = (BookHolder) lectern;
            } else {
                return false;
            }
            ItemStack book = bookHolder.zps$getBook();
            if (book != null && book.getItem() instanceof WritableBookItem) {
                int page = bookHolder.zps$getCurrentPage();
                if (page < 0) {
                    return false;
                }

                CompoundTag tag = book.getOrCreateTag();
                if (!tag.contains("pages", Tag.TAG_LIST)) {
                    tag.put("pages", new ListTag());
                }

                ListTag pages = tag.getList("pages", 8);
                while (pages.size() <= page) {
                    // fix book having no pages or unexpected desynchronization
                    pages.add(StringTag.valueOf(""));
                    bookHolder.zps$onPageAdded();
                }

                boolean newText = !currentDisplayText.equals(lastWrittenText);

                if (newText || lastPrintTime < 0) {
                    if (lastPrintTime >= 0) {
                        if (pages.size() < 100 && bookHolder.zps$getCurrentPage() + 1 >= pages.size()) {
                            pages.add(StringTag.valueOf(""));
                            bookHolder.zps$onPageAdded();
                        }
                        bookHolder.zps$cyclePages();
                    }
                    lastWrittenText = this.currentDisplayText;
                    pages.set(bookHolder.zps$getCurrentPage(), StringTag.valueOf(BookPageTextLimiter.truncateToDisplayableLength(this.currentDisplayText)));
                    bookHolder.zps$onPageWritten();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("LastPrintTime", lastPrintTime);
        if (lastWrittenText != null) {
            tag.putString("LastWrittenText", lastWrittenText);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        lastPrintTime = tag.contains("LastPrintTime") ? tag.getLong("LastPrintTime") : -20L;
        if (tag.contains("LastWrittenText")) {
            lastWrittenText = tag.getString("LastWrittenText");
        }
    }
}
