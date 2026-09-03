package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.blockentity.AssemblerBlockEntity;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.Shaped5x5Recipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * JEI category for {@link Shaped5x5Recipe}: the 5x5 pattern, an arrow, and the result. Drawn with JEI's
 * own slot sprites (no background texture), like {@link RollingMillCategory}. Only loaded when JEI is
 * installed.
 */
public class Shaped5x5Category implements IRecipeCategory<Shaped5x5Recipe> {
    public static final RecipeType<Shaped5x5Recipe> TYPE =
            new RecipeType<>(ZPSMod.resource("shaped_5x5"), Shaped5x5Recipe.class);

    private static final int SLOT = 18;
    private static final int GRID_LEFT = 0;
    private static final int GRID_TOP = 0;
    private static final int GRID_SIZE = SLOT * AssemblerBlockEntity.GRID_WIDTH; // 90
    private static final int ARROW_LEFT = GRID_SIZE + 6;
    private static final int OUTPUT_LEFT = ARROW_LEFT + 30;
    private static final int WIDTH = OUTPUT_LEFT + SLOT;
    private static final int HEIGHT = GRID_SIZE;

    private final IDrawable icon;
    private final IDrawable arrow;

    public Shaped5x5Category(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.ASSEMBLER.get());
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<Shaped5x5Recipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.zps.jei.shaped_5x5");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public ResourceLocation getRegistryName(Shaped5x5Recipe recipe) {
        return recipe.getId();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Shaped5x5Recipe recipe, IFocusGroup focuses) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe.getWidth();
        int height = recipe.getHeight();
        // Centre patterns smaller than 5x5 in the grid, matching how the Assembler lays them out.
        int offsetX = (AssemblerBlockEntity.GRID_WIDTH - width) / 2;
        int offsetY = (AssemblerBlockEntity.GRID_HEIGHT - height) / 2;
        // Every cell of the grid gets a slot, empty ones included: the blanks are part of the recipe
        // (the Assembler wants those cells clear), so the whole 5x5 is drawn rather than just the
        // filled cells.
        for (int row = 0; row < AssemblerBlockEntity.GRID_HEIGHT; row++) {
            for (int col = 0; col < AssemblerBlockEntity.GRID_WIDTH; col++) {
                IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT,
                                GRID_LEFT + col * SLOT + 1,
                                GRID_TOP + row * SLOT + 1)
                        .setStandardSlotBackground();

                int patternCol = col - offsetX;
                int patternRow = row - offsetY;
                if (patternCol < 0 || patternCol >= width || patternRow < 0 || patternRow >= height) {
                    continue;
                }
                Ingredient ingredient = ingredients.get(patternCol + patternRow * width);
                if (!ingredient.isEmpty()) {
                    slot.addIngredients(ingredient);
                }
            }
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_LEFT, (HEIGHT - SLOT) / 2 + 1)
                .setOutputSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(Shaped5x5Recipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, ARROW_LEFT, (HEIGHT - arrow.getHeight()) / 2);
    }
}
