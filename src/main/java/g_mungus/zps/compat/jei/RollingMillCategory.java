package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.RollingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI category for {@link RollingRecipe}. Shows the input ingredient, an arrow, and the rolled
 * result. Only loaded when JEI is installed.
 */
public class RollingMillCategory implements IRecipeCategory<RollingRecipe> {
    public static final RecipeType<RollingRecipe> TYPE =
            new RecipeType<>(ZPSMod.resource("rolling"), RollingRecipe.class);

    private static final int WIDTH = 100;
    private static final int HEIGHT = 26;
    private static final int SLOT_Y = 4;

    private final IDrawable icon;
    private final IDrawable arrow;

    public RollingMillCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.ROLLING_MILL.get());
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RollingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.zps.rolling_mill");
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
    public ResourceLocation getRegistryName(RollingRecipe recipe) {
        return recipe.getId();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RollingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 3, SLOT_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.ingredient());
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 21, SLOT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(RollingRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        // Vertically centre the arrow against the 18px slot row.
        arrow.draw(graphics, (WIDTH - arrow.getWidth()) / 2, SLOT_Y + (18 - arrow.getHeight()) / 2);
    }
}
