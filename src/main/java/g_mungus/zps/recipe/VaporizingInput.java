package g_mungus.zps.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The Vaporizer's ingredient slots, presented to {@link VaporizingRecipe} for matching. A read-only
 * snapshot: recipes in this version take a {@link Container}, so it is one, but nothing writes to it.
 */
public record VaporizingInput(List<ItemStack> items) implements Container {

    public static VaporizingInput of(IItemHandler handler) {
        List<ItemStack> items = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            items.add(handler.getStackInSlot(slot));
        }
        return new VaporizingInput(items);
    }

    @Override
    public @NotNull ItemStack getItem(int index) {
        return items.get(index);
    }

    public int size() {
        return items.size();
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return false;
    }

    @Override
    public void clearContent() {
    }
}
