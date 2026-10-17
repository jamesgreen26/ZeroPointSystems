package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.LargeSpaceDoorBlock;
import g_mungus.zps.blockentity.LargeSpaceDoorBlockEntity;
import g_mungus.zps.mixin.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.SortedSet;

/**
 * Draws both halves of a large space door from its controller (the bottom middle block). The halves meet along
 * the texture's diagonal seam and slide apart sideways until each clears the doorway.
 */
public class LargeSpaceDoorBlockEntityRenderer implements BlockEntityRenderer<LargeSpaceDoorBlockEntity> {
    public static final ModelResourceLocation LEFT_MODEL =
            ModelResourceLocation.standalone(ZPSMod.resource("block/decor/large_space_door_left"));
    public static final ModelResourceLocation RIGHT_MODEL =
            ModelResourceLocation.standalone(ZPSMod.resource("block/decor/large_space_door_right"));

    private static final float GROW = 1.002f;
    private static final float DOOR_CENTRE_Z = 0.25f + 1.5f / 16;

    public LargeSpaceDoorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(LargeSpaceDoorBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }

    @Override
    public void render(LargeSpaceDoorBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = blockEntity.getBlockState();
        float open = blockEntity.openness(partialTick);
        float left = -open * LargeSpaceDoorBlock.OPEN_TRAVEL;
        float right = open * LargeSpaceDoorBlock.OPEN_TRAVEL;
        // Vanilla only cracks a block entity's own block; mining any other part cracks the whole door too.
        int crackStage = crackStage(blockEntity.getBlockPos(), state);
        VertexConsumer crack = crackStage < 0 ? null : new SheetedDecalTextureGenerator(
                Minecraft.getInstance().renderBuffers().crumblingBufferSource()
                        .getBuffer(ModelBakery.DESTROY_TYPES.get(crackStage)),
                poseStack.last(), 1.0f);

        poseStack.pushPose();
        // Door space: +x runs left to right as seen from the front, +z points out of the front.
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getValue(LargeSpaceDoorBlock.FACING).toYRot()));
        // Grown a hair about the door's centre so its edges sink into the surrounding blocks instead of z-fighting.
        poseStack.translate(0, 1.5f, DOOR_CENTRE_Z);
        poseStack.scale(GROW, GROW, GROW);
        poseStack.translate(0, -1.5f, -DOOR_CENTRE_Z);
        // The models span -16..32px, a pixel in from the front like the block's outline.
        poseStack.translate(-0.5f, 1, 0.25f);

        VertexConsumer cutout = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        renderHalf(poseStack, cutout, state, LEFT_MODEL, left, packedLight, packedOverlay);
        renderHalf(poseStack, cutout, state, RIGHT_MODEL, right, packedLight, packedOverlay);
        if (crack != null) {
            renderHalf(poseStack, crack, state, LEFT_MODEL, left, packedLight, packedOverlay);
            renderHalf(poseStack, crack, state, RIGHT_MODEL, right, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    /** The furthest crack on any part but the controller, which vanilla cracks itself; -1 when none is mined. */
    private static int crackStage(BlockPos controller, BlockState state) {
        var progress = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).zps$getDestructionProgress();
        int stage = -1;
        for (LargeSpaceDoorBlock.Part part : LargeSpaceDoorBlock.Part.values()) {
            if (part == LargeSpaceDoorBlock.Part.BOTTOM) {
                continue;
            }
            SortedSet<BlockDestructionProgress> mining =
                    progress.get(LargeSpaceDoorBlock.partPos(controller, state, part).asLong());
            if (mining != null && !mining.isEmpty()) {
                stage = Math.max(stage, mining.last().getProgress());
            }
        }
        return Math.min(stage, ModelBakery.DESTROY_TYPES.size() - 1);
    }

    private static void renderHalf(PoseStack poseStack, VertexConsumer buffer, BlockState state,
                                   ModelResourceLocation model, float offset, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(offset, 0, 0);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), buffer, state,
                minecraft.getModelManager().getModel(model), 1, 1, 1, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
