package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.ThrusterBlock;
import g_mungus.zps.blockentity.gas.ThrusterPropellant;
import g_mungus.zps.item.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.api.recipe.KelvinGasIngredient;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;
import org.valkyrienskies.kelvin.integration.jei.GasSlots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * JEI page for what a thruster does with each gas at a full signal: the gas going in and what leaves the nozzle,
 * each a second's worth, with the burn rate and thrust on bars beneath, laid out as every {@link MachinePage} is,
 * the thruster's nozzle pointing at the output. Every registered gas gets a page, so looking up a gas's uses finds it:
 * those with a {@link g_mungus.zps.recipe.ThrusterFuelRecipe} as it says, the rest as they pass through unburnt.
 */
public class ThrusterCategory implements IRecipeCategory<ThrusterCategory.Fuel> {
    public static final RecipeType<Fuel> TYPE = RecipeType.create(ZPSMod.MOD_ID, "thruster", Fuel.class);

    /** One page: a feed of nothing but {@code gas}, and what the thruster does with it. */
    public record Fuel(GasType gas, ThrusterPropellant propellant) {
    }

    private static final MachinePage PAGE = new MachinePage(1, 1, 1, 1, 2);

    /** The thruster as it sits on the page: nozzle to the right, where the output is, lit as if running. */
    private static final BlockState THRUSTER = ModBlocks.THRUSTER.get().defaultBlockState()
            .setValue(ThrusterBlock.FACING, Direction.EAST)
            .setValue(ThrusterBlock.POWER, 15);

    private final IDrawable icon;
    private final IDrawable slot;

    public ThrusterCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.THRUSTER.get());
        this.slot = guiHelper.getSlotDrawable();
    }

    /** A page for every gas, by the recipes {@code level} has: the fuels first, weakest first, then the rest by name. */
    public static List<Fuel> fuels(Level level) {
        List<Fuel> fuels = new ArrayList<>();
        GasTypeRegistry.INSTANCE.getGAS_TYPES().values().forEach(gas -> fuels.add(new Fuel(gas, ThrusterPropellant.of(level, gas))));
        fuels.sort(Comparator.comparing((Fuel fuel) -> fuel.propellant().isUnburnt())
                .thenComparingDouble(fuel -> fuel.propellant().thrust())
                .thenComparing(fuel -> fuel.gas().getName()));
        return fuels;
    }

    @Override
    public RecipeType<Fuel> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.zps.thruster");
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
    public ResourceLocation getRegistryName(Fuel fuel) {
        ResourceLocation gas = fuel.gas().getResourceLocation();
        return ZPSMod.resource("thruster/" + gas.getNamespace() + "/" + gas.getPath());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Fuel fuel, IFocusGroup focuses) {
        GasType gas = fuel.gas();
        ThrusterPropellant propellant = fuel.propellant();
        double kgPerSecond = kgPerSecond(fuel);
        Component perSecond = Component.translatable("jei.zps.thruster.per_second").withStyle(ChatFormatting.GRAY);

        GasSlots.INSTANCE.addInputGasSlot(builder, PAGE.inputX(0), PAGE.inputY(0), new KelvinGasIngredient(gas, kgPerSecond), slot)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(perSecond));

        // The same mass leaves as went in: as the recipe's result, or as itself.
        GasType result = propellant.result() != null ? propellant.result() : gas;
        Component leaves = (Double.isNaN(propellant.temperatureK())
                ? Component.translatable("jei.zps.thruster.leaves_unburnt")
                : Component.translatable("jei.zps.thruster.leaves_hot", Math.round(propellant.temperatureK())))
                .withStyle(ChatFormatting.GRAY);
        GasSlots.INSTANCE.addOutputGasSlot(builder, PAGE.outputX(0), PAGE.outputY(0), new KelvinGasIngredient(result, kgPerSecond), slot)
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(perSecond);
                    tooltip.add(leaves);
                });
    }

    private static double kgPerSecond(Fuel fuel) {
        return fuel.propellant().fullDrawKgPerSecond();
    }

    @Override
    public void draw(Fuel fuel, IRecipeSlotsView slotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        PAGE.drawStage(graphics);
        MachinePage.drawBlock(graphics, THRUSTER);
        PAGE.drawBar(graphics, 0, Component.translatable("jei.zps.thruster.burn_rate", formatMass(kgPerSecond(fuel))));
        PAGE.drawBar(graphics, 1, Component.translatable("jei.zps.thruster.thrust", Math.round(fuel.propellant().fullThrust())));
    }

    /** Grams to a tenth under a hundred, whole grams under a kilogram, kilograms to a hundredth above. */
    private static String formatMass(double kg) {
        if (kg >= 1) {
            return String.format("%.2f kg", kg);
        }
        double grams = kg * 1000;
        return grams < 100 ? String.format("%.1f g", grams) : String.format("%.0f g", grams);
    }
}
