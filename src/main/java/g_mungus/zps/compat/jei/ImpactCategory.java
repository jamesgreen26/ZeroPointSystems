package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.client.tooltip.ItemIconsTooltip;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ImpactRecipe;
import g_mungus.zps.recipe.ImpactResult;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        var inputSlot = builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .addItemStacks(inputs);
        // An item icon shows the block as it sits in an inventory. Where the recipe asks for a
        // particular state, draw that instead: a full composter, not an empty one.
        Map<Item, BlockState> requiredStates = requiredStates(recipe);
        if (!requiredStates.isEmpty()) {
            inputSlot.setCustomRenderer(VanillaTypes.ITEM_STACK, new BlockStateSlotRenderer(requiredStates));
        }
        // An item cannot show a block state, so a recipe that wants e.g. a full composter says so here.
        List<Component> requirements = stateRequirements(recipe);
        if (!requirements.isEmpty()) {
            inputSlot.addRichTooltipCallback((view, tooltip) -> requirements.forEach(tooltip::add));
        }

        List<ImpactResult> results = recipe.results();
        for (int i = 0; i < results.size(); i++) {
            ImpactResult result = results.get(i);
            Block resultBlock = result.block().value();
            ItemStack resultStack = displayStack(resultBlock);
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUTS_X + (i * SLOT_SIZE), SLOT_Y)
                    .setStandardSlotBackground()
                    .addItemStack(resultStack);
            if (resultBlock.asItem() == Items.AIR && !resultStack.isEmpty()) {
                // The borrowed item would show the wrong block, so draw the real one over it.
                slot.setCustomRenderer(VanillaTypes.ITEM_STACK,
                        new BlockStateSlotRenderer(Map.of(resultStack.getItem(), resultBlock.defaultBlockState())));
            }
            List<ItemStack> buried = buriedCandidates(result);
            if (buried.isEmpty()) {
                continue;
            }
            // Whatever a suspicious block may be hiding goes in its tooltip, as icons.
            slot.addRichTooltipCallback((view, tooltip) -> {
                tooltip.add(Component.translatable("gui.zps.jei.impact.may_contain").withStyle(ChatFormatting.GRAY));
                tooltip.add(new ItemIconsTooltip(buried));
            });
            // The buried items are outputs too, just not ones with a slot of their own. Declaring
            // them lets a "how do I get this" lookup on a nugget or raw ore land on this recipe.
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(buried);
        }
    }

    /**
     * The stack standing in for {@code block} in a slot. A block with no item of its own, like the
     * dirt-filled composter, borrows its pick-block item and wears the block's name over it.
     */
    private static ItemStack displayStack(Block block) {
        ItemStack stack = new ItemStack(block);
        if (!stack.isEmpty()) {
            return stack;
        }
        stack = block.getCloneItemStack(Minecraft.getInstance().level, BlockPos.ZERO, block.defaultBlockState());
        if (!stack.isEmpty()) {
            stack.set(DataComponents.ITEM_NAME, block.getName());
        }
        return stack;
    }

    /**
     * For a recipe with a state requirement, the state to draw for each input block: the first one
     * the predicate accepts. Empty when the recipe takes the block in any state.
     */
    private static Map<Item, BlockState> requiredStates(ImpactRecipe recipe) {
        if (recipe.properties().isEmpty()) {
            return Map.of();
        }
        StatePropertiesPredicate predicate = recipe.properties().get();
        Map<Item, BlockState> states = new HashMap<>();
        for (Holder<Block> block : recipe.ingredient()) {
            Item item = block.value().asItem();
            if (item == Items.AIR) {
                continue;
            }
            block.value().getStateDefinition().getPossibleStates().stream()
                    .filter(predicate::matches)
                    .findFirst()
                    .ifPresent(state -> states.put(item, state));
        }
        return states;
    }

    /**
     * One line per state property the recipe pins down, e.g. "Requires level: 8". Worked out from
     * which states the predicate accepts, since the predicate does not expose its own entries.
     */
    private static List<Component> stateRequirements(ImpactRecipe recipe) {
        if (recipe.properties().isEmpty()) {
            return List.of();
        }
        StatePropertiesPredicate predicate = recipe.properties().get();
        Set<String> seen = new LinkedHashSet<>();
        List<Component> lines = new ArrayList<>();
        for (Holder<Block> block : recipe.ingredient()) {
            List<BlockState> accepted = block.value().getStateDefinition().getPossibleStates().stream()
                    .filter(predicate::matches)
                    .toList();
            for (Property<?> property : block.value().getStateDefinition().getProperties()) {
                Set<String> values = accepted.stream()
                        .map(state -> valueName(state, property))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (values.isEmpty() || values.size() == property.getPossibleValues().size()) {
                    continue;
                }
                String joined = String.join(", ", values);
                if (seen.add(property.getName() + "=" + joined)) {
                    lines.add(Component.translatable("gui.zps.jei.impact.requires_state", property.getName(), joined)
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
        return lines;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** The items a result may bury. An empty tag resolves to a placeholder barrier, which is not one of them. */
    private static List<ItemStack> buriedCandidates(ImpactResult result) {
        return result.buriedItem()
                .map(buried -> Arrays.stream(buried.getItems()).filter(stack -> !stack.is(Items.BARRIER)).toList())
                .orElse(List.of());
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
