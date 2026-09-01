package g_mungus.zps.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jetbrains.annotations.NotNull;

/** The two ingredient slots of a Vaporizer, as a recipe input. */
public record VaporizingInput(ItemStack primary, ItemStack secondary) implements RecipeInput {

    @Override
    public @NotNull ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> primary;
            case 1 -> secondary;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return primary.isEmpty() && secondary.isEmpty();
    }
}
