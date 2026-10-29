package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.ThrusterBlock;
import g_mungus.zps.blockentity.gas.ThrusterPropellant;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ThrusterFlame;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
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
        PAGE.drawBlock(graphics, THRUSTER);
        drawExhaust(graphics, fuel.propellant().flame());
        PAGE.drawBar(graphics, 0, Component.translatable("jei.zps.thruster.burn_rate", formatMass(kgPerSecond(fuel))));
        PAGE.drawBar(graphics, 1, Component.translatable("jei.zps.thruster.thrust", Math.round(fuel.propellant().fullThrust())));
    }

    // --- exhaust -----------------------------------------------------------------------------

    /*
     * A light stand-in for the world's plume: flat squares out of the nozzle towards the output, coloured as the
     * plume colours its particles. Nothing is kept between frames; each particle is worked out from the clock and its
     * own seed, so it costs only the squares drawn.
     */
    private static final int PARTICLES = 32;
    private static final long LIFETIME_MILLIS = 900;
    /** How far past the nozzle face a particle gets, in blocks, and how wide the stream is there. */
    private static final float REACH = 2.1f;
    private static final float START_SPREAD = 0.08f;
    private static final float END_SPREAD = 0.4f;
    private static final float SPEED_JITTER = 0.35f;
    /** Particles start this many pixels square and grow to the larger once a quarter through their life. */
    private static final int SMALL_SIZE = 3;
    private static final int LARGE_SIZE = 4;

    private static void drawExhaust(GuiGraphics graphics, ThrusterFlame flame) {
        // Wrapped well within a float's precision; the jump every few minutes is one frame of one particle.
        float clock = (System.currentTimeMillis() % (LIFETIME_MILLIS * 100_000L)) / (float) LIFETIME_MILLIS;
        for (int i = 0; i < PARTICLES; i++) {
            float life = clock + i / (float) PARTICLES;
            int generation = (int) life;
            float age = life - generation;
            int seed = scramble(i * 0x9E3779B9 + generation);
            float speed = 1 - SPEED_JITTER / 2 + SPEED_JITTER * unit(seed, 0);
            float spread = START_SPREAD + (END_SPREAD - START_SPREAD) * age;
            // The nozzle is the east face; the stream leaves it along east, spreading across it.
            Vector3f at = MachinePage.toPage(1 + age * REACH * speed,
                    0.5f + (unit(seed, 1) - 0.5f) * 2 * spread,
                    0.5f + (unit(seed, 2) - 0.5f) * 2 * spread);
            int colour = exhaustColour(flame, age);
            if ((colour >>> 24) == 0) {
                continue;
            }
            int size = age < 0.25f ? SMALL_SIZE : LARGE_SIZE;
            int x = Math.round(at.x) - size / 2;
            int y = Math.round(at.y) - size / 2;
            graphics.fill(x, y, x + size, y + size, Math.round(at.z), colour);
        }
    }

    /** A particle's colour by fraction of life, as the world's plume shader has it: fire, then smoke. */
    private static int exhaustColour(ThrusterFlame flame, float t) {
        float[] c;
        if (flame == ThrusterFlame.COLD) {
            // No fire: a puff that thins out sooner than smoke. Bolder than the world's pale one, which would vanish
            // against the page's grey: near-white, cooling to slate.
            c = mix(new float[]{0.94f, 0.98f, 1.00f, 0.95f}, new float[]{0.45f, 0.52f, 0.60f, 0.80f}, smoothstep(0, 0.5f, t));
            c[3] *= 1 - smoothstep(0.5f, 1, t);
            return argb(c);
        }
        boolean blue = flame == ThrusterFlame.BLUE;
        float[] white = blue ? new float[]{0.88f, 0.96f, 1.00f, 0.90f} : new float[]{1.00f, 0.97f, 0.85f, 0.90f};
        float[] yellow = blue ? new float[]{0.45f, 0.82f, 1.00f, 0.88f} : new float[]{1.00f, 0.82f, 0.32f, 0.88f};
        float[] orange = blue ? new float[]{0.12f, 0.38f, 1.00f, 0.80f} : new float[]{1.00f, 0.42f, 0.08f, 0.80f};
        float[] ember = blue ? new float[]{0.08f, 0.12f, 0.42f, 0.65f} : new float[]{0.42f, 0.17f, 0.08f, 0.65f};
        float[] dark = {0.20f, 0.19f, 0.18f, 0.55f};
        float[] grey = {0.42f, 0.42f, 0.42f, 0.40f};
        c = mix(white, yellow, smoothstep(0.00f, 0.06f, t));
        c = mix(c, orange, smoothstep(0.06f, 0.15f, t));
        c = mix(c, ember, smoothstep(0.15f, 0.27f, t));
        c = mix(c, dark, smoothstep(0.27f, 0.38f, t));
        c = mix(c, grey, smoothstep(0.38f, 0.65f, t));
        c[3] *= 1 - smoothstep(0.55f, 1, t);
        return argb(c);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        float[] out = new float[4];
        for (int i = 0; i < 4; i++) {
            out[i] = a[i] + (b[i] - a[i]) * t;
        }
        return out;
    }

    private static float smoothstep(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static int argb(float[] c) {
        return Math.round(c[3] * 255) << 24 | Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8
                | Math.round(c[2] * 255);
    }

    /** Scrambles {@code x} into a well-spread seed. */
    private static int scramble(int x) {
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        return x ^ x >>> 16;
    }

    /** The {@code n}th number from 0 to 1 that {@code seed} gives. */
    private static float unit(int seed, int n) {
        return (scramble(seed + n * 0x632BE5AB) >>> 8) / (float) (1 << 24);
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
