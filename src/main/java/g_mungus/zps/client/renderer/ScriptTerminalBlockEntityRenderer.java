package g_mungus.zps.client.renderer;

import com.mojang.math.Axis;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class ScriptTerminalBlockEntityRenderer implements BlockEntityRenderer<ScriptTerminalBlockEntity> {
    private static final ItemStack PAPER = new ItemStack(Items.PAPER);
    private final ItemRenderer itemRenderer;

    public ScriptTerminalBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ScriptTerminalBlockEntity blockEntity, float partialTick, com.mojang.blaze3d.vertex.@NotNull PoseStack poseStack,
                       net.minecraft.client.renderer.@NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState blockState = blockEntity.getBlockState();
        if (!blockState.getValue(ScriptTerminalBlock.HAS_ADDRESS_PAD)) return;

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.0625F, 0.5F);
        float g = blockState.getValue(ScriptTerminalBlock.FACING).getClockWise().toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(-g));
        poseStack.mulPose(Axis.ZP.rotationDegrees(67.5F));
        poseStack.translate(0.0F, -0.125F, 0.0F);
        poseStack.scale(0.5F, 0.5F, 0.5F);

        itemRenderer.renderStatic(
                PAPER,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                0
        );

        poseStack.popPose();
    }
}
