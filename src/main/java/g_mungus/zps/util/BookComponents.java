package g_mungus.zps.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Book page access matching the 1.21.1 helper of the same name, over 1.20.1's book NBT, so code
 * ported from 1.21.1 reads the same on both branches.
 */
public final class BookComponents {
    private BookComponents() {}

    public static int getPageCount(ItemStack book) {
        CompoundTag tag = book.getTag();
        if (tag == null || !(book.is(Items.WRITABLE_BOOK) || book.is(Items.WRITTEN_BOOK))) {
            return 0;
        }
        return tag.getList("pages", Tag.TAG_STRING).size();
    }

    public static String getPageText(ItemStack book, int page, boolean filtered) {
        CompoundTag tag = book.getTag();
        if (page < 0 || tag == null) {
            return "";
        }

        ListTag pages = tag.getList("pages", Tag.TAG_STRING);
        if (page >= pages.size()) {
            return "";
        }
        String raw = pages.getString(page);
        if (filtered) {
            CompoundTag filteredPages = tag.getCompound("filtered_pages");
            String key = String.valueOf(page);
            if (filteredPages.contains(key, Tag.TAG_STRING)) {
                raw = filteredPages.getString(key);
            }
        }

        if (book.is(Items.WRITABLE_BOOK)) {
            return raw;
        }
        if (book.is(Items.WRITTEN_BOOK)) {
            try {
                Component component = Component.Serializer.fromJson(raw);
                return component == null ? raw : component.getString();
            } catch (Exception e) {
                return raw;
            }
        }
        return "";
    }

    public static int setWritablePage(ItemStack book, int page, String text) {
        CompoundTag tag = book.getOrCreateTag();
        ListTag pages = tag.getList("pages", Tag.TAG_STRING);
        int added = 0;
        while (pages.size() <= page) {
            pages.add(StringTag.valueOf(""));
            added++;
        }
        pages.set(page, StringTag.valueOf(text));
        tag.put("pages", pages);
        return added;
    }

    public static ItemStack signWritableBook(ItemStack book, String title, String author) {
        CompoundTag source = book.getTag();
        ListTag writablePages = source == null ? new ListTag() : source.getList("pages", Tag.TAG_STRING);
        ListTag pages = new ListTag();
        for (int i = 0; i < writablePages.size(); i++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(writablePages.getString(i)))));
        }

        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = written.getOrCreateTag();
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putBoolean("resolved", true);
        tag.put("pages", pages);
        return written;
    }

    public static ItemStack parse(CompoundTag tag) {
        return ItemStack.of(tag);
    }

    public static CompoundTag save(ItemStack stack) {
        return stack.save(new CompoundTag());
    }
}
