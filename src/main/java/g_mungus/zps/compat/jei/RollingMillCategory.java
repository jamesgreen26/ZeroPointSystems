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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;

/**
 * JEI category for {@link RollingRecipe}: the input, the mill with its rollers turning, and the rolled result, laid
 * out as every {@link MachinePage} is. Only loaded when JEI is installed.
 */
public class RollingMillCategory implements IRecipeCategory<RecipeHolder<RollingRecipe>> {
    public static final RecipeType<RecipeHolder<RollingRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ZPSMod.resource("rolling"));

    private static final MachinePage PAGE = new MachinePage(1, 1, 1, 1, 0);

    /** Facing the viewer, the rollers' world spin carrying the work between them from the input to the output. */
    private static final Direction FACING = Direction.SOUTH;
    private static final BlockState MILL = ModBlocks.ROLLING_MILL.get().defaultBlockState()
            .setValue(RollingMillBlock.FACING, FACING)
            .setValue(RollingMillBlock.WORKING, true);
    private static final float DEGREES_PER_SECOND = 90;

    private final IDrawable icon;
    private final IDrawable slot;

    public RollingMillCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.ROLLING_MILL.get());
        this.slot = guiHelper.getSlotDrawable();
    }

    @Override
    public RecipeType<RecipeHolder<RollingRecipe>> getRecipeType() {
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
    public ResourceLocation getRegistryName(RecipeHolder<RollingRecipe> holder) {
        return holder.id();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<RollingRecipe> holder, IFocusGroup focuses) {
        RollingRecipe recipe = holder.value();
        builder.addSlot(RecipeIngredientRole.INPUT, PAGE.inputX(0), PAGE.inputY(0))
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.ingredient());
        builder.addSlot(RecipeIngredientRole.OUTPUT, PAGE.outputX(0), PAGE.outputY(0))
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<RollingRecipe> holder, IRecipeSlotsView slotsView, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        PAGE.drawStage(graphics);
        float angle = (System.currentTimeMillis() % 360_000L) / 1000f * DEGREES_PER_SECOND;
        MachinePage.drawMachine(graphics, MachinePage.BLOCK_SCALE, MachinePage.BLOCK_Y, (pose, buffers) -> {
            MachinePage.renderBlock(pose, buffers, MILL);
            RollingMillBlockEntityRenderer.renderRollers(Minecraft.getInstance().getItemRenderer(), pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FACING, angle);
        });
    }
}
