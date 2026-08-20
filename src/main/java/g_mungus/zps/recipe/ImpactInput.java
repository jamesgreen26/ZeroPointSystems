package g_mungus.zps.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Recipe input for the Impact Piston: the block state directly beneath the machine.
 *
 * <p>There are no item slots involved, so the {@link RecipeInput} item accessors are inert.
 */
public record ImpactInput(BlockState state) implements RecipeInput {
    @Override
    public @NotNull ItemStack getItem(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 0;
    }

    /**
     * Always false. {@code RecipeManager.getRecipeFor} short-circuits to an empty result for inputs
     * that report themselves empty, and the default implementation would do exactly that here since
     * this input holds no items.
     */
    @Override
    public boolean isEmpty() {
        return false;
    }
}
