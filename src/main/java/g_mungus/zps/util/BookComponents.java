package g_mungus.zps.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

public final class BookComponents {
    private BookComponents() {}

    public static int getPageCount(ItemStack book) {
        if (book.is(Items.WRITABLE_BOOK)) {
            return book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY).pages().size();
        }
        if (book.is(Items.WRITTEN_BOOK)) {
            return book.getOrDefault(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY).pages().size();
        }
        return 0;
    }

    public static String getPageText(ItemStack book, int page, boolean filtered) {
        if (page < 0) {
            return "";
        }

        if (book.is(Items.WRITABLE_BOOK)) {
            List<Filterable<String>> pages = book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY).pages();
            return page < pages.size() ? pages.get(page).get(filtered) : "";
        }

        if (book.is(Items.WRITTEN_BOOK)) {
            List<Component> pages = book.getOrDefault(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY).getPages(filtered);
            return page < pages.size() ? pages.get(page).getString() : "";
        }

        return "";
    }

    public static int setWritablePage(ItemStack book, int page, String text) {
        WritableBookContent content = book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
        List<Filterable<String>> pages = new ArrayList<>(content.pages());
        int added = 0;
        while (pages.size() <= page) {
            pages.add(Filterable.passThrough(""));
            added++;
        }
        pages.set(page, Filterable.passThrough(text));
        book.set(DataComponents.WRITABLE_BOOK_CONTENT, content.withReplacedPages(pages));
        return added;
    }

    public static ItemStack signWritableBook(ItemStack book, String title, String author) {
        WritableBookContent writable = book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
        List<Filterable<Component>> pages = new ArrayList<>(writable.pages().size());
        for (Filterable<String> page : writable.pages()) {
            pages.add(page.map(text -> (Component) Component.literal(text)));
        }

        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        written.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, pages, true));
        return written;
    }

    public static ItemStack parse(HolderLookup.Provider registries, CompoundTag tag) {
        return ItemStack.parseOptional(registries, tag);
    }

    public static CompoundTag save(HolderLookup.Provider registries, ItemStack stack) {
        return (CompoundTag) stack.save(registries, new CompoundTag());
    }
}
