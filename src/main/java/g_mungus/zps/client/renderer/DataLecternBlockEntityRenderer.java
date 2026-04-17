package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.block.cableNetwork.light_pipe.DataLecternBlock;
import g_mungus.zps.blockentity.light_pipe.DataLecternBlockEntity;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class DataLecternBlockEntityRenderer implements BlockEntityRenderer<DataLecternBlockEntity> {

    private final BookModel bookModel;

    public DataLecternBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(DataLecternBlockEntity blockEntity, float partialTick, com.mojang.blaze3d.vertex.@NotNull PoseStack poseStack,
                       net.minecraft.client.renderer.@NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        BlockState blockState = blockEntity.getBlockState();
        if (blockState.getValue(DataLecternBlock.HAS_BOOK)) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 1.0625F, 0.5F);
            float g = (blockState.getValue(DataLecternBlock.FACING)).getClockWise().toYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(-g));
            poseStack.mulPose(Axis.ZP.rotationDegrees(67.5F));
            poseStack.translate(0.0F, -0.125F, 0.0F);
            this.bookModel.setupAnim(0.0F, 0.1F, 0.9F, 1.2F);
            VertexConsumer vertexConsumer = EnchantTableRenderer.BOOK_LOCATION.buffer(bufferSource, RenderType::entitySolid);
            this.bookModel.render(poseStack, vertexConsumer, packedLight, packedOverlay, 0xFFFFFFFF);
            poseStack.popPose();
        }
    }
}
