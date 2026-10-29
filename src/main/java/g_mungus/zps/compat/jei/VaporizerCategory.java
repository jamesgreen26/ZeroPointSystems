package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.GasOutput;
import g_mungus.zps.recipe.VaporizingRecipe;
import g_mungus.zps.recipe.VaporizingRecipeSerializer;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient;
import org.valkyrienskies.kelvin.integration.jei.GasSlots;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI category for {@link VaporizingRecipe}: the items going in, the vaporizer, and the gas coming out as Kelvin's own
 * gas ingredients, with the temperature figures on bars beneath, laid out as every {@link MachinePage} is.
 */
public class VaporizerCategory implements IRecipeCategory<VaporizingRecipe> {
    public static final RecipeType<VaporizingRecipe> TYPE =
            new RecipeType<>(ZPSMod.resource("vaporizing"), VaporizingRecipe.class);

    /** As many to a row as the vaporizer has slots. */
    private static final int INPUT_COLUMNS = VaporizingRecipeSerializer.MAX_INGREDIENTS;
    private static final int OUTPUT_COLUMNS = 2;
    /** The two figures every vaporizing recipe carries: a floor and a cost. */
    private static final int BARS = 2;

    private static final BlockState VAPORIZER = ModBlocks.VAPORIZER.get().defaultBlockState();

    private final IDrawable icon;
    private final IDrawable slot;

    /** The last recipe laid out, for {@link #getHeight}, which JEI asks without saying which recipe. */
    @Nullable
    private VaporizingRecipe currentRecipe;

    public VaporizerCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.VAPORIZER.get());
        this.slot = guiHelper.getSlotDrawable();
    }

    private static MachinePage page(VaporizingRecipe recipe) {
        int inputs = Math.max(1, recipe.ingredients().size());
        int outputs = Math.max(1, recipe.results().size());
        return new MachinePage(inputs, Math.min(inputs, INPUT_COLUMNS), outputs, Math.min(outputs, OUTPUT_COLUMNS), BARS);
    }

    @Override
    public RecipeType<VaporizingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.zps.vaporizer");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return MachinePage.WIDTH;
    }

    @Override
    public int getHeight() {
        return currentRecipe == null ? new MachinePage(1, 1, 1, 1, BARS).height() : page(currentRecipe).height();
    }

    @Override
    public ResourceLocation getRegistryName(VaporizingRecipe recipe) {
        return recipe.getId();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, VaporizingRecipe recipe, IFocusGroup focuses) {
        currentRecipe = recipe;
        MachinePage page = page(recipe);

        List<Ingredient> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, page.inputX(i), page.inputY(i))
                    .setBackground(slot, -1, -1)
                    .addIngredients(ingredients.get(i));
        }

        List<KelvinGasIngredient> outputs = resolvedOutputs(recipe);
        for (int i = 0; i < outputs.size(); i++) {
            GasSlots.INSTANCE.addOutputGasSlot(builder, page.outputX(i), page.outputY(i), outputs.get(i), slot);
        }
    }

    /** The recipe's outputs as Kelvin ingredients, skipping any gas no mod has registered. */
    private static List<KelvinGasIngredient> resolvedOutputs(VaporizingRecipe recipe) {
        List<KelvinGasIngredient> outputs = new ArrayList<>();
        for (GasOutput output : recipe.results()) {
            GasType gas = output.resolve();
            if (gas != null) {
                outputs.add(new KelvinGasIngredient(gas, output.massKg()));
            }
        }
        return outputs;
    }

    @Override
    public void draw(VaporizingRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        MachinePage page = page(recipe);
        page.drawStage(graphics);
        page.drawBlock(graphics, VAPORIZER);
        // Kelvin's own key for the floor, so the wording matches its gas reaction pages; the cost is ours.
        page.drawBar(graphics, 0, Component.translatable("kelvin.requirements.min_temperature", recipe.minTemperature()));
        page.drawBar(graphics, 1, Component.translatable("jei.zps.vaporizing.temperature_cost", recipe.temperatureCost()));
    }
}
