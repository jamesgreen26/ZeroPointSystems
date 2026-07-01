package g_mungus.zps.client.recipebook;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RollingMillRecipeBookComponent extends RecipeBookComponent {
    private static final Component FILTER_NAME = Component.translatable("gui.recipebook.toggleRecipes.rollable");

    @Override
    protected Component getRecipeFilterName() {
        return FILTER_NAME;
    }

    @Override
    public void setupGhostRecipe(RecipeHolder<?> recipe, List<Slot> slots) {
        ItemStack result = recipe.value().getResultItem(this.minecraft.level.registryAccess());
        this.ghostRecipe.setRecipe(recipe);
        this.ghostRecipe.addIngredient(Ingredient.of(result), slots.get(1).x, slots.get(1).y);

        NonNullList<Ingredient> ingredients = recipe.value().getIngredients();
        if (!ingredients.isEmpty() && !ingredients.getFirst().isEmpty()) {
            this.ghostRecipe.addIngredient(ingredients.getFirst(), slots.get(0).x, slots.get(0).y);
        }
    }

    public boolean hasGhostRecipe() {
        return this.ghostRecipe.getRecipe() != null && this.ghostRecipe.size() > 0;
    }

    public boolean isGhostSlot(Slot slot) {
        if (!hasGhostRecipe()) {
            return false;
        }

        for (int i = 0; i < this.ghostRecipe.size(); i++) {
            if (this.ghostRecipe.get(i).getX() == slot.x && this.ghostRecipe.get(i).getY() == slot.y) {
                return true;
            }
        }

        return false;
    }
}
