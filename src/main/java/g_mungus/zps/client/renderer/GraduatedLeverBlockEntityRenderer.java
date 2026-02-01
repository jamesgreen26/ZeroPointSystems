package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import g_mungus.zps.block.cableNetwork.GraduatedLeverBlock;
import g_mungus.zps.block.cableNetwork.PanelBlock;
import g_mungus.zps.blockentity.GraduatedLeverBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.NotNull;

public class GraduatedLeverBlockEntityRenderer implements BlockEntityRenderer<GraduatedLeverBlockEntity> {

    final Font font;

    public GraduatedLeverBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }
    @Override
    public void render(
            GraduatedLeverBlockEntity blockEntity,
            float partialTick,
            @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState blockState = blockEntity.getBlockState();
        Direction facing = blockState.getValue(PanelBlock.FACING);
        AttachFace face = blockState.getValue(PanelBlock.FACE);

        poseStack.pushPose();

        // Center on block
        poseStack.translate(0.5, 0.5, 0.5);

        // Rotate based on facing direction
        switch (facing) {
            case SOUTH -> {}
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }

        // Rotate based on face (ceiling/floor/wall)
        switch (face) {
            case CEILING -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case FLOOR -> poseStack.mulPose(Axis.XP.rotationDegrees(270));
            case WALL -> {}
        }

        // Move slightly off the face to prevent z-fighting
        poseStack.translate(0, 0, 0.001);

        float scale = 1.0f / 128.0f;
        poseStack.scale(scale, -scale, scale);

        String text = String.valueOf(blockState.getValue(GraduatedLeverBlock.POWER));

        if (text.length() == 1) {
            text = "0" + text;
        }
        int maxCols = 2;

        int cellSize = 8; // font pixels per character cell

        // Top-left origin of the grid
        int originX = -40;
        int originY = -40;

        for (int i = 0; i < text.length() && i < maxCols; i++) {
            char c = text.charAt(i);

            int col = i % maxCols;

            int x = originX + col * cellSize;

            this.font.drawInBatch(
                    String.valueOf(c),
                    x,
                    originY,
                    0xFFFFFF,
                    false,
                    poseStack.last().pose(),
                    bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET,
                    0,
                    packedLight
            );
        }

        poseStack.popPose();
    }

}
