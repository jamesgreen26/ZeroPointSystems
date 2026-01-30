package g_mungus.zps.blockentity.light_pipe;

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

public class PrintingPressBlockEntity extends AbstractTextDataReceiver {
    public PrintingPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTING_PRESS.get(), pos, state);
    }

    private long lastPrintTime = -20L;

    public void tick() {
        if (level == null) return;
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
                    pages.set(bookHolder.zps$getCurrentPage(), StringTag.valueOf(this.currentDisplayText));
                    bookHolder.onPageWritten();
                    return true;
                }
            }
        }
        return false;
    }
}
