package g_mungus.zps.client.renderer;

import com.mojang.math.Axis;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import g_mungus.zps.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class ScriptTerminalBlockEntityRenderer implements BlockEntityRenderer<ScriptTerminalBlockEntity> {
    private static final ItemStack ADDRESS_PAD_STACK = new ItemStack(ModItems.ADDRESS_PAD.get());
    private static final ModelResourceLocation ADDRESS_PAD_BER_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(ZPSMod.MOD_ID, "item/address_pad_ber"));
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
        poseStack.translate(0.5F, 0.875F, 0.5F);
        float g = blockState.getValue(ScriptTerminalBlock.FACING).getClockWise().toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(-(g + 90)));
        poseStack.mulPose(Axis.XP.rotationDegrees(-22.5F));
        poseStack.translate(0.0F, -0.375F, 0.0F);

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(ADDRESS_PAD_BER_MODEL);
        itemRenderer.render(
                ADDRESS_PAD_STACK,
                ItemDisplayContext.NONE,
                false,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay,
                model
        );

        poseStack.popPose();
    }
}
