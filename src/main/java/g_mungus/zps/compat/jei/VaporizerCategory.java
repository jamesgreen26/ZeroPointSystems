package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.GasOutput;
import g_mungus.zps.recipe.VaporizingRecipe;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient;
import org.valkyrienskies.kelvin.integration.jei.GasSlots;
import org.valkyrienskies.kelvin.integration.jei.SpriteDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI category for {@link VaporizingRecipe}, laid out to match Kelvin's gas reaction category so
 * the two read as one family: item inputs top-left, the furnace arrow across the top, the gas
 * outputs top-right as Kelvin's own gas ingredients, and the temperature figures underneath on
 * Kelvin's stippled requirement bars.
 */
public class VaporizerCategory implements IRecipeCategory<RecipeHolder<VaporizingRecipe>> {
    public static final RecipeType<RecipeHolder<VaporizingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ZPSMod.resource("vaporizing"));

    // Kelvin's geometry, so the two categories line up when a player flips between them.
    private static final int WIDTH = 177;
    private static final int INPUTS_PER_ROW = 3;
    private static final int OUTPUTS_PER_ROW = 2;
    private static final int SLOT_PITCH = 19;
    private static final int SLOT_ROW_HEIGHT = 19;
    private static final int REQUIREMENT_HEIGHT = 20;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;
    /** Kelvin's magic inset that puts the outputs the same distance from the border as the inputs. */
    private static final int OUTPUT_RIGHT_INSET = 25;

    private static final int BAR_LEFT = 4;
    private static final int BAR_WIDTH = 169;
    private static final int BAR_HEIGHT = 19;
    private static final int BAR_BORDER = 2;
    private static final int BAR_COLOR = 0xFF363636;
    private static final int BAR_TEXT_X = 7;
    private static final int BAR_TEXT_Y_OFFSET = 5;
    private static final int BAR_TEXT_COLOR = 0xFFFFFF;

    /** The two figures every vaporizing recipe carries: a floor and a cost. */
    private static final int REQUIREMENT_COUNT = 2;

    private static final ResourceLocation FURNACE_ARROW_SPRITE =
            ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    @Nullable
    private RecipeHolder<VaporizingRecipe> currentRecipe;

    public VaporizerCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.VAPORIZER.get());
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = new SpriteDrawable(ARROW_WIDTH, ARROW_HEIGHT, FURNACE_ARROW_SPRITE);
    }

    @Override
    public RecipeType<RecipeHolder<VaporizingRecipe>> getRecipeType() {
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
        return WIDTH;
    }

    /** Sized like Kelvin's: a row of slots per three inputs or two outputs, then the bars. */
    @Override
    public int getHeight() {
        int slotRows = 1;
        if (currentRecipe != null) {
            VaporizingRecipe recipe = currentRecipe.value();
            slotRows = Math.max(1, Math.max(
                    ceilDiv(recipe.ingredients().size(), INPUTS_PER_ROW),
                    ceilDiv(recipe.results().size(), OUTPUTS_PER_ROW)));
        }
        return slotRows * SLOT_ROW_HEIGHT + 1 + REQUIREMENT_COUNT * REQUIREMENT_HEIGHT;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    @Override
    public ResourceLocation getRegistryName(RecipeHolder<VaporizingRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<VaporizingRecipe> holder, IFocusGroup focuses) {
        currentRecipe = holder;
        VaporizingRecipe recipe = holder.value();

        List<Ingredient> ingredients = recipe.ingredients();
        int xOffset = ingredients.size() < INPUTS_PER_ROW
                ? (INPUTS_PER_ROW - ingredients.size()) * SLOT_PITCH / 2
                : 0;
        for (int i = 0; i < ingredients.size(); i++) {
            int x = xOffset + (i % INPUTS_PER_ROW) * SLOT_PITCH;
            int y = (i / INPUTS_PER_ROW) * SLOT_ROW_HEIGHT;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setBackground(slot, -1, -1)
                    .addIngredients(ingredients.get(i));
        }

        List<KelvinGasIngredient> outputs = resolvedOutputs(recipe);
        xOffset = outputs.size() < OUTPUTS_PER_ROW
                ? (OUTPUTS_PER_ROW - outputs.size()) * SLOT_PITCH / 2
                : 0;
        for (int i = 0; i < outputs.size(); i++) {
            int x = WIDTH - (xOffset + (i % OUTPUTS_PER_ROW) * SLOT_PITCH) - OUTPUT_RIGHT_INSET;
            int y = (i / OUTPUTS_PER_ROW) * SLOT_ROW_HEIGHT;
            GasSlots.INSTANCE.addOutputGasSlot(builder, x, y, outputs.get(i), slot);
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
    public void draw(RecipeHolder<VaporizingRecipe> holder, IRecipeSlotsView slotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        VaporizingRecipe recipe = holder.value();
        arrow.draw(graphics, WIDTH / 2 - ARROW_WIDTH / 2, 0);

        int requirementsTop = getHeight() - REQUIREMENT_COUNT * REQUIREMENT_HEIGHT;
        // Kelvin's own key for the floor, so the wording is identical; the cost is ours.
        drawRequirement(graphics, requirementsTop,
                Component.translatable("kelvin.requirements.min_temperature", recipe.minTemperature()));
        drawRequirement(graphics, requirementsTop + REQUIREMENT_HEIGHT,
                Component.translatable("jei.zps.vaporizing.temperature_cost", recipe.temperatureCost()));
    }

    private static void drawRequirement(GuiGraphics graphics, int y, Component text) {
        drawRequirementBar(graphics, BAR_LEFT, y);
        graphics.drawString(Minecraft.getInstance().font, text, BAR_TEXT_X, y + BAR_TEXT_Y_OFFSET, BAR_TEXT_COLOR);
    }

    /**
     * Kelvin's requirement bar, which is Create's "darker bar" drawn by hand: a two pixel border
     * stippled on a checkerboard, so the outline reads soft instead of as a hard frame.
     */
    private static void drawRequirementBar(GuiGraphics graphics, int left, int top) {
        for (int y = 0; y < BAR_HEIGHT; y++) {
            boolean onHorizontalEdge = y < BAR_BORDER || y >= BAR_HEIGHT - BAR_BORDER;
            for (int x = 0; x < BAR_WIDTH; x++) {
                boolean onVerticalEdge = x < BAR_BORDER || x >= BAR_WIDTH - BAR_BORDER;
                if (!onHorizontalEdge && !onVerticalEdge) {
                    continue;
                }
                if ((x + y) % 2 == 0) {
                    continue;
                }
                graphics.fill(left + x, top + y, left + x + 1, top + y + 1, BAR_COLOR);
            }
        }
    }
}
