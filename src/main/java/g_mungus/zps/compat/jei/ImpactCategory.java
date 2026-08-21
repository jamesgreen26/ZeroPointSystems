package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ImpactRecipe;
import g_mungus.zps.recipe.ImpactResult;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI category for {@link ImpactRecipe}. Shows the struck block, an arrow, and every weighted
 * outcome with its chance. Only loaded when JEI is installed.
 */
public class ImpactCategory implements IRecipeCategory<RecipeHolder<ImpactRecipe>> {
    public static final RecipeType<RecipeHolder<ImpactRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ZPSMod.resource("impact"));

    private static final int SLOT_SIZE = 22;
    private static final int SLOT_Y = 4;
    private static final int CHANCE_HEIGHT = 10;
    private static final int HEIGHT = SLOT_Y + SLOT_SIZE + CHANCE_HEIGHT;
    private static final int INPUT_X = 3;
    private static final int ARROW_GAP = 32;
    private static final int OUTPUTS_X = INPUT_X + SLOT_SIZE + ARROW_GAP + 4;
    /** Outputs are laid out in a row, so the width grows with the number of possible results. */
    private static final int MAX_OUTPUTS_SHOWN = 4;

    private final IDrawable icon;
    private final IDrawable arrow;

    public ImpactCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.IMPACT_PISTON.get());
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<ImpactRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.zps.jei.impact");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return OUTPUTS_X + (SLOT_SIZE * MAX_OUTPUTS_SHOWN) + 3;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<ImpactRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ImpactRecipe> holder, IFocusGroup focuses) {
        ImpactRecipe recipe = holder.value();

        // The input is a block set, so the slot cycles through every block the piston accepts.
        List<ItemStack> inputs = new ArrayList<>();
        for (Holder<Block> block : recipe.ingredient()) {
            ItemStack stack = new ItemStack(block.value());
            if (!stack.isEmpty()) {
                inputs.add(stack);
            }
        }
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .addItemStacks(inputs);

        List<ImpactResult> results = recipe.results();
        for (int i = 0; i < results.size(); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUTS_X + (i * SLOT_SIZE), SLOT_Y)
                    .setStandardSlotBackground()
                    .addItemStack(new ItemStack(results.get(i).block().value()));
        }
    }

    @Override
    public void draw(RecipeHolder<ImpactRecipe> holder, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        // Vertically centre the arrow against the 18px slot row.
        arrow.draw(graphics, INPUT_X + SLOT_SIZE + ((ARROW_GAP - arrow.getWidth()) / 2), SLOT_Y + (SLOT_SIZE - arrow.getHeight()) / 2);

        List<ImpactResult> results = holder.value().results();
        if (results.size() <= 1) {
            return;
        }
        // Multiple outcomes: label each with its share of the total weight.
        int total = 0;
        for (ImpactResult result : results) {
            total += result.weight();
        }
        var font = Minecraft.getInstance().font;
        int chanceY = SLOT_Y + SLOT_SIZE + 1;
        for (int i = 0; i < results.size(); i++) {
            String text = formatChance(results.get(i).weight(), total);
            int centre = OUTPUTS_X + (i * SLOT_SIZE) + (SLOT_SIZE / 2);
            graphics.drawString(font, text, centre - (font.width(text) / 2), chanceY, 0xFF808080, false);
        }
    }

    /** Trims the trailing ".0" so whole percentages read as "90%" rather than "90.0%". */
    private static String formatChance(int weight, int total) {
        double percent = (weight * 100.0) / total;
        String text = String.format("%.1f", percent);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + "%";
    }
}
