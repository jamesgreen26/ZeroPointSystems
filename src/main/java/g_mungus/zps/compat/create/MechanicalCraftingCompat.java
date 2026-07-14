package g_mungus.zps.compat.create;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInventory;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Create mechanical-crafting integration for the assembler. All direct {@code com.simibubi.create.*}
 * references are confined to this class, which must only ever be touched behind
 * {@link g_mungus.zps.compat.Compat#isCreateLoaded()} so it never links when Create is absent.
 */
@ApiStatus.Internal
public final class MechanicalCraftingCompat {

    private MechanicalCraftingCompat() {
    }

    /**
     * Attempts to resolve a Create mechanical crafting recipe for the given {@code width}x{@code height}
     * grid (row-major, top row first). Returns the assembled result, or {@code null} if no mechanical
     * recipe matches. Create's {@link MechanicalCraftingInventory} is required because
     * {@code MechanicalCraftingRecipe#matches} expects Create's grouped-item layout.
     */
    @Nullable
    public static ItemStack tryAssemble(Level level, List<ItemStack> grid, int width, int height) {
        // Build Create's GroupedItems via its public NBT reader. Create maps the largest grid-y onto the top
        // crafting row, so flip the visual row to preserve orientation.
        ListTag gridNbt = new ListTag();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                ItemStack stack = grid.get(col + row * width);
                if (stack.isEmpty()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putInt("x", col);
                entry.putInt("y", height - 1 - row);
                entry.put("item", stack.save(new CompoundTag()));
                gridNbt.add(entry);
            }
        }
        if (gridNbt.isEmpty()) {
            return null;
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("Grid", gridNbt);

        GroupedItems items = GroupedItems.read(nbt);
        items.calcStats();
        CraftingContainer input = new MechanicalCraftingInventory(items);

        Optional<CraftingRecipe> found = AllRecipeTypes.MECHANICAL_CRAFTING.find(input, level);
        return found.map(recipe -> recipe.assemble(input, level.registryAccess())).orElse(null);
    }

    /**
     * All Create mechanical crafting recipes. Each {@code MechanicalCraftingRecipe} is a vanilla
     * {@link net.minecraft.world.item.crafting.ShapedRecipe}, so callers can read its ingredients/width/
     * height through the vanilla API without linking any Create type.
     */
    public static List<CraftingRecipe> mechanicalCraftingRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.MECHANICAL_CRAFTING.getType());
    }
}
