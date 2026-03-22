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

import java.util.List;

public class DataLecternDisplayTarget extends DisplayTarget {

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

        boolean changed = false;
        for (int i = 0; i - line < text.size() && i < 50; i++) {
            if (tag.size() <= i)
                tag.add(StringTag.valueOf(i < line ? "" : Component.Serializer.toJson(text.get(i - line))));
            else if (i >= line) {
                if (i - line == 0)
                    reserve(i, lectern, context);
                if (i - line > 0 && isReserved(i - line, lectern, context))
                    break;
                tag.set(i, StringTag.valueOf(Component.Serializer.toJson(text.get(i - line))));
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
        return new DisplayTargetStats(50, 256, this);
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
