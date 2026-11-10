package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.CondensingRecipe;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient;
import org.valkyrienskies.kelvin.integration.jei.GasSlots;

/**
 * JEI category for {@link CondensingRecipe}: the gas going in, the condenser, and the item coming
 * out, with the temperature figures on bars beneath, laid out as every {@link MachinePage} is.
 */
public class CryoCondenserCategory implements IRecipeCategory<RecipeHolder<CondensingRecipe>> {
    public static final RecipeType<RecipeHolder<CondensingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ZPSMod.resource("condensing"));

    /** The two figures every condensing recipe carries: a ceiling and a cost. */
    private static final int BARS = 2;
    private static final MachinePage PAGE = new MachinePage(1, 1, 1, 1, BARS);

    private static final BlockState CONDENSER = ModBlocks.CRYO_CONDENSER.get().defaultBlockState();

    private final IDrawable icon;
    private final IDrawable slot;

    public CryoCondenserCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.CRYO_CONDENSER.get());
        this.slot = guiHelper.getSlotDrawable();
    }

    @Override
    public RecipeType<RecipeHolder<CondensingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.zps.cryo_condenser");
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
        return PAGE.height();
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<CondensingRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CondensingRecipe> holder, IFocusGroup focuses) {
        CondensingRecipe recipe = holder.value();
        GasType gas = recipe.ingredient().resolve();
        if (gas != null) {
            GasSlots.INSTANCE.addInputGasSlot(builder, PAGE.inputX(0), PAGE.inputY(0),
                    new KelvinGasIngredient(gas, recipe.ingredient().massKg()), slot);
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, PAGE.outputX(0), PAGE.outputY(0))
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<CondensingRecipe> holder, IRecipeSlotsView slotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        CondensingRecipe recipe = holder.value();
        PAGE.drawStage(graphics);
        PAGE.drawBlock(graphics, CONDENSER);
        // Kelvin's own key for the ceiling, so the wording matches its gas reaction pages; the cost is ours.
        PAGE.drawBar(graphics, 0, Component.translatable("kelvin.requirements.max_temperature", recipe.maxTemperature()));
        PAGE.drawBar(graphics, 1, Component.translatable("jei.zps.condensing.temperature_cost",
                recipe.temperatureCost(), CondensingRecipe.REFERENCE_GAS_TEMPERATURE_K));
    }
}
