package g_mungus.zps.compat.create;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.utility.CreateLang;
import g_mungus.zps.blockentity.light_pipe.DataLecternBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class DataLecternDisplayTarget extends DisplayTarget {

    /** Lines a written book page shows before overflowing; vanilla's book screen draws 14. */
    private static final int LINES_PER_PAGE = 14;

    private static final int MAX_PAGES = 50;

    /**
     * Groups the source's lines onto pages, {@link #LINES_PER_PAGE} to a page, joined with
     * newlines. Create's own lectern target writes one line per page; here a multi-line reading
     * such as a reactor status or an item list fills a page before it spills to the next.
     */
    private static List<MutableComponent> paginate(List<MutableComponent> lines) {
        List<MutableComponent> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += LINES_PER_PAGE) {
            MutableComponent page = Component.empty();
            int end = Math.min(lines.size(), start + LINES_PER_PAGE);
            for (int i = start; i < end; i++) {
                if (i > start)
                    page.append(Component.literal("\n"));
                page.append(lines.get(i));
            }
            pages.add(page);
        }
        return pages;
    }

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        BlockEntity be = context.getTargetBlockEntity();
        if (!(be instanceof DataLecternBlockEntity lectern))
            return;
        ItemStack book = lectern.getBook();
        if (book.isEmpty())
            return;

        if (book.is(Items.WRITABLE_BOOK))
            lectern.setBook(book = signBook(book));
        if (!book.is(Items.WRITTEN_BOOK))
            return;

        ListTag tag = book.getTag().getList("pages", Tag.TAG_STRING);
        List<MutableComponent> pageTexts = paginate(text);

        boolean changed = false;
        for (int i = 0; i - line < pageTexts.size() && i < MAX_PAGES; i++) {
            if (tag.size() <= i)
                tag.add(StringTag.valueOf(i < line ? "" : Component.Serializer.toJson(pageTexts.get(i - line))));
            else if (i >= line) {
                if (i - line == 0)
                    reserve(i, lectern, context);
                if (i - line > 0 && isReserved(i - line, lectern, context))
                    break;
                tag.set(i, StringTag.valueOf(Component.Serializer.toJson(pageTexts.get(i - line))));
            }
            changed = true;
        }

        book.getTag().put("pages", tag);
        lectern.setBook(book);

        if (changed)
            context.level().sendBlockUpdated(context.getTargetPos(), lectern.getBlockState(), lectern.getBlockState(), 2);
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(MAX_PAGES * LINES_PER_PAGE, 256, this);
    }

    @Override
    public Component getLineOptionText(int line) {
        return CreateLang.translateDirect("display_target.page", line + 1);
    }

    private ItemStack signBook(ItemStack book) {
        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getTag();
        if (tag != null)
            written.setTag(tag.copy());
        written.addTagElement("author", StringTag.valueOf("Data Gatherer"));
        written.addTagElement("filtered_title", StringTag.valueOf("Printed Book"));
        written.addTagElement("title", StringTag.valueOf("Printed Book"));
        return written;
    }

    @Override
    public boolean requiresComponentSanitization() {
        return true;
    }
}
