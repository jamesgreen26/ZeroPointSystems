package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TextDisplayBlockEntityRenderer implements BlockEntityRenderer<TextDisplayBlockEntity> {

    Font font;

    public TextDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }
    @Override
    public void render(TextDisplayBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        BlockState blockState = blockEntity.getBlockState();
        Direction facing = blockState.getValue(TextDisplayBlock.FACING);

        poseStack.pushPose();

        poseStack.translate(0.5, 0.5, 0.5);

        switch (facing) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case SOUTH -> { /* Default orientation */ }
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }

        poseStack.translate(0, 0, 0.501);

        poseStack.scale(0.125f, -0.125f, 0.125f);

        String text = blockEntity.getDisplayText();
        float textWidth = this.font.width(text);
        float textHeight = this.font.lineHeight;

        float x = 0.5f -textWidth / 2.0f;
        float y = 0.5f -textHeight / 2.0f;

        this.font.drawInBatch(text, x, y, 0xFFFFFF, false, poseStack.last().pose(),
                bufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, 255);

        poseStack.popPose();
    }
}
