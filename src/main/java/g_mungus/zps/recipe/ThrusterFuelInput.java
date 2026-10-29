package g_mungus.zps.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.kelvin.api.GasType;

/** A gas reaching a thruster, presented to {@link ThrusterFuelRecipe} for matching. Holds no items. */
public record ThrusterFuelInput(GasType gas) implements RecipeInput {

    @Override
    public @NotNull ItemStack getItem(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 0;
    }

    /** Never empty: with no items, the default would call it empty and the recipe manager would skip it. */
    @Override
    public boolean isEmpty() {
        return false;
    }
}
