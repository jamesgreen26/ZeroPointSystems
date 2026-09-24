package g_mungus.zps.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Draws a slot's item as a particular block state instead of as its item icon.
 *
 * <p>An item icon can only ever show a block's inventory look, which for a composter is the empty
 * one. Impact recipes care about the state in the world: a full composter going in, a dirt-filled
 * one (which has no item at all) coming out. The slot still holds an ordinary item stack, so JEI
 * lookups and tooltips keep working; only the picture changes.
 */
public class BlockStateSlotRenderer implements IIngredientRenderer<ItemStack> {
    private final Map<Item, BlockState> states;

    /** @param states the state to draw for each item; any other item is drawn as usual */
    public BlockStateSlotRenderer(Map<Item, BlockState> states) {
        this.states = Map.copyOf(states);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull ItemStack stack) {
        BlockState state = states.get(stack.getItem());
        if (state == null) {
            graphics.renderItem(stack, 0, 0);
            return;
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        // The same frame vanilla sets up for an item icon: centred in the 16px slot, y flipped.
        pose.translate(8.0f, 8.0f, 150.0f);
        pose.scale(16.0f, -16.0f, 16.0f);
        // The "gui" display transform every block item inherits from minecraft:block/block.
        pose.mulPose(Axis.XP.rotationDegrees(30.0f));
        pose.mulPose(Axis.YP.rotationDegrees(225.0f));
        pose.scale(0.625f, 0.625f, 0.625f);
        pose.translate(-0.5f, -0.5f, -0.5f);

        Lighting.setupFor3DItems();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, pose, graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        graphics.flush();
        pose.popPose();
    }

    @SuppressWarnings("removal")
    @Override
    public @NotNull List<Component> getTooltip(@NotNull ItemStack stack, @NotNull TooltipFlag flag) {
        Minecraft minecraft = Minecraft.getInstance();
        return stack.getTooltipLines(minecraft.player, flag);
    }
}
