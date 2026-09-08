package g_mungus.zps.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** The Vaporizer's ingredient slots, presented to {@link VaporizingRecipe} for matching. */
public record VaporizingInput(List<ItemStack> items) implements RecipeInput {

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

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }
}
