package g_mungus.zps.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * each a second's worth, with the burn rate and thrust on bars beneath. Laid out like Create's fan processing
 * pages, the machine drawn in 3D over a shadow with a long arrow from the input to the output under it, but drawn
 * by hand rather than from Create's sheet. Every registered gas gets a page, so looking up a gas's uses finds it:
 * those with a {@link g_mungus.zps.recipe.ThrusterFuelRecipe} as it says, the rest as they pass through unburnt.
 */
public class ThrusterCategory implements IRecipeCategory<ThrusterCategory.Fuel> {
    public static final RecipeType<Fuel> TYPE = RecipeType.create(ZPSMod.MOD_ID, "thruster", Fuel.class);

    /** One page: a feed of nothing but {@code gas}, and what the thruster does with it. */
    public record Fuel(GasType gas, ThrusterPropellant propellant) {
    }

    // Kelvin's width, so this lines up with the vaporizer and gas reaction pages.
    private static final int WIDTH = 177;
    private static final int SLOT_Y = 38;
    private static final int SLOT_INSET = 20;
    private static final int INPUT_X = SLOT_INSET;
    private static final int OUTPUT_X = WIDTH - SLOT_INSET - 16;

    private static final int ARROW_LEFT = INPUT_X + 22;
    private static final int ARROW_RIGHT = OUTPUT_X - 6;
    private static final int ARROW_Y = SLOT_Y + 7;
    private static final int ARROW_HEAD = 4;
    private static final int ARROW_COLOR = 0xFFA0A0A0;
    private static final int ARROW_SHADOW_COLOR = 0xFF4A4A4A;

    private static final int BLOCK_X = WIDTH / 2;
    private static final int BLOCK_Y = 18;
    private static final float BLOCK_SCALE = 22;
    private static final int SHADOW_WIDTH = 44;
    private static final int SHADOW_HEIGHT = 10;
    private static final int SHADOW_Y = 29;

    private static final int BARS_TOP = 62;
    private static final int BAR_PITCH = 20;
    private static final int BAR_COUNT = 2;
    private static final int BAR_LEFT = 4;
    private static final int BAR_TEXT_X = 7;
    private static final int BAR_TEXT_Y_OFFSET = 5;
    private static final int BAR_TEXT_COLOR = 0xFFFFFF;
    private static final int HEIGHT = BARS_TOP + BAR_COUNT * BAR_PITCH;

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
        GasTypeRegistry.INSTANCE.getGasTypes().forEach(gas -> fuels.add(new Fuel(gas, ThrusterPropellant.of(level, gas))));
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
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
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

        GasSlots.INSTANCE.addInputGasSlot(builder, INPUT_X, SLOT_Y, new KelvinGasIngredient(gas, kgPerSecond), slot)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(perSecond));

        // The same mass leaves as went in: as the recipe's result, or as itself.
        GasType result = propellant.result() != null ? propellant.result() : gas;
        Component leaves = (Double.isNaN(propellant.temperatureK())
                ? Component.translatable("jei.zps.thruster.leaves_unburnt")
                : Component.translatable("jei.zps.thruster.leaves_hot", Math.round(propellant.temperatureK())))
                .withStyle(ChatFormatting.GRAY);
        GasSlots.INSTANCE.addOutputGasSlot(builder, OUTPUT_X, SLOT_Y, new KelvinGasIngredient(result, kgPerSecond), slot)
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
        drawArrow(graphics);
        drawShadow(graphics);
        drawThruster(graphics);

        drawBar(graphics, BARS_TOP,
                Component.translatable("jei.zps.thruster.burn_rate", formatMass(kgPerSecond(fuel))));
        drawBar(graphics, BARS_TOP + BAR_PITCH,
                Component.translatable("jei.zps.thruster.thrust", Math.round(fuel.propellant().fullThrust())));
    }

    /** Grams to a tenth under a hundred, whole grams under a kilogram, kilograms to a hundredth above. */
    private static String formatMass(double kg) {
        if (kg >= 1) {
            return String.format("%.2f kg", kg);
        }
        double grams = kg * 1000;
        return grams < 100 ? String.format("%.1f g", grams) : String.format("%.0f g", grams);
    }

    /** A long arrow from the input to the output, with a drop shadow one pixel down and right. */
    private static void drawArrow(GuiGraphics graphics) {
        drawArrowShape(graphics, 1, ARROW_SHADOW_COLOR);
        drawArrowShape(graphics, 0, ARROW_COLOR);
    }

    private static void drawArrowShape(GuiGraphics graphics, int offset, int color) {
        int tip = ARROW_RIGHT + offset;
        int y = ARROW_Y + offset;
        graphics.fill(ARROW_LEFT + offset, y, tip - ARROW_HEAD, y + 2, color);
        // The head: a column per pixel, narrowing to the tip.
        for (int i = 0; i < ARROW_HEAD + 1; i++) {
            int half = ARROW_HEAD - i;
            graphics.fill(tip - ARROW_HEAD - 1 + i, y - half + 1, tip - ARROW_HEAD + i, y + 1 + half, color);
        }
    }

    /** A soft dark ellipse under the block, darkest in the middle. */
    private static void drawShadow(GuiGraphics graphics) {
        double rx = SHADOW_WIDTH / 2.0;
        double ry = SHADOW_HEIGHT / 2.0;
        int left = BLOCK_X - SHADOW_WIDTH / 2;
        for (int y = 0; y < SHADOW_HEIGHT; y++) {
            for (int x = 0; x < SHADOW_WIDTH; x++) {
                double dx = (x + 0.5 - rx) / rx;
                double dy = (y + 0.5 - ry) / ry;
                double d = dx * dx + dy * dy;
                if (d >= 1) {
                    continue;
                }
                int alpha = (int) (0x50 * (1 - d));
                graphics.fill(left + x, SHADOW_Y + y, left + x + 1, SHADOW_Y + y + 1, alpha << 24);
            }
        }
    }

    /** The thruster in 3D, tilted to show its top and turned to show the nozzle pointing at the output. */
    private static void drawThruster(GuiGraphics graphics) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(BLOCK_X, BLOCK_Y, 100);
        pose.scale(BLOCK_SCALE, -BLOCK_SCALE, BLOCK_SCALE);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        pose.mulPose(Axis.YP.rotationDegrees(-35));
        pose.translate(-0.5f, -0.5f, -0.5f);

        Lighting.setupFor3DItems();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                THRUSTER, pose, graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        graphics.flush();
        Lighting.setupForFlatItems();
        pose.popPose();
    }

    private static void drawBar(GuiGraphics graphics, int y, Component text) {
        VaporizerCategory.drawRequirementBar(graphics, BAR_LEFT, y);
        graphics.drawString(Minecraft.getInstance().font, text, BAR_TEXT_X, y + BAR_TEXT_Y_OFFSET, BAR_TEXT_COLOR);
    }
}
