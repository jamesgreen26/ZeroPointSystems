package g_mungus.zps.blockentity.light_pipe;

import g_mungus.zps.block.cableNetwork.light_pipe.DataTranscriberBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
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

public class DataTranscriberBlockEntity extends AbstractTextDataReceiver {
    public DataTranscriberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_TRANSCRIBER.get(), pos, state);
    }

    private long lastPrintTime = -20L;

    public void tick() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(DataTranscriberBlock.POWERED) || !state.getValue(DataTranscriberBlock.POWERED)) return;
        long time = level.getGameTime();
        if (lastPrintTime + 10 < time) {
            lastPrintTime = time;
            boolean shouldPlayEffects = writeText();
            if (shouldPlayEffects) {
                level.playSound(null, getBlockPos(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1f, 1f);
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
                    pages.add(StringTag.valueOf(""));
                }

                if (!pages.getString(bookHolder.zps$getCurrentPage()).equals(this.currentDisplayText)) {
                    pages.set(bookHolder.zps$getCurrentPage(), StringTag.valueOf(truncateStringToDisplayableLength(this.currentDisplayText)));
                    bookHolder.onPageWritten();
                    return true;
                }
            }
        }
        return false;
    }

    private static String truncateStringToDisplayableLength(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        // If the length is less than 512, probably this text was handwritten and is safe to use
        if (original.length() < 512) {
            return original;
        }

        final int MAX_LINES = 14;
        final int MAX_CHARS_PER_LINE = 19;
        final int MAX_TOTAL_CHARS = 1023;

        int lineCount = 1;
        int lineLength = 0;
        int i = 0;

        for (; i < original.length() && i < MAX_TOTAL_CHARS; i++) {
            char c = original.charAt(i);

            if (c == '\n') {
                lineCount++;
                if (lineCount > MAX_LINES) {
                    break;
                }
                lineLength = 0;
                continue;
            }

            lineLength++;
            if (lineLength > MAX_CHARS_PER_LINE) {
                lineCount++;
                if (lineCount > MAX_LINES) {
                    break;
                }
                lineLength = 1; // this char starts the new wrapped line
            }
        }

        return original.substring(0, i);
    }

}
