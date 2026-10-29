package g_mungus.zps.compat.jei;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.RollingMillBlock;
import g_mungus.zps.client.renderer.RollingMillBlockEntityRenderer;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * JEI category for {@link RollingRecipe}: the input, the mill with its rollers turning, and the rolled result, with
 * how many seconds it takes on the arrow, laid out as every {@link MachinePage} is. Only loaded when JEI is installed.
 */
public class RollingMillCategory implements IRecipeCategory<RollingRecipe> {
    public static final RecipeType<RollingRecipe> TYPE =
            new RecipeType<>(ZPSMod.resource("rolling"), RollingRecipe.class);

    private static final MachinePage PAGE = new MachinePage(1, 1, 1, 1, 0);

    /** Facing the viewer, the rollers' world spin carrying the work between them from the input to the output. */
    private static final Direction FACING = Direction.SOUTH;
    private static final BlockState MILL = ModBlocks.ROLLING_MILL.get().defaultBlockState()
            .setValue(RollingMillBlock.FACING, FACING)
            .setValue(RollingMillBlock.WORKING, true);
    private static final float DEGREES_PER_SECOND = 90;
    private static final int TICKS_PER_SECOND = 20;

    private final IDrawable icon;
    private final IDrawable slot;

    public RollingMillCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.ROLLING_MILL.get());
        this.slot = guiHelper.getSlotDrawable();
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
        return MachinePage.WIDTH;
    }

    @Override
    public int getHeight() {
        return PAGE.height();
    }

    @Override
    public ResourceLocation getRegistryName(RollingRecipe recipe) {
        return recipe.getId();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RollingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, PAGE.inputX(0), PAGE.inputY(0))
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.ingredient());
        builder.addSlot(RecipeIngredientRole.OUTPUT, PAGE.outputX(0), PAGE.outputY(0))
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(RollingRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        PAGE.drawStage(graphics);
        float angle = (System.currentTimeMillis() % 360_000L) / 1000f * DEGREES_PER_SECOND;
        MachinePage.drawMachine(graphics, MachinePage.BLOCK_SCALE, MachinePage.BLOCK_Y, (pose, buffers) -> {
            MachinePage.renderBlock(pose, buffers, MILL);
            RollingMillBlockEntityRenderer.renderRollers(Minecraft.getInstance().getItemRenderer(), pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FACING, angle);
        });
        MachinePage.drawArrowLabel(graphics, Component.translatable("jei.zps.rolling.time", seconds(recipe.processTime())));
    }

    /** Ticks as seconds, to a tenth only where it isn't whole: 10, 7.5. */
    private static String seconds(int ticks) {
        return ticks % TICKS_PER_SECOND == 0
                ? Integer.toString(ticks / TICKS_PER_SECOND)
                : String.format("%.1f", ticks / (float) TICKS_PER_SECOND);
    }
}
