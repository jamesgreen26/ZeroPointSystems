package g_mungus.zps.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.gas.GasGaugeBlock;
import g_mungus.zps.blockentity.gas.GasGaugeBlockEntity;
import g_mungus.zps.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the gauge's needle, swept across the dial by the block entity's normalised reading.
 *
 * <p>The dial face and hub are part of the block model; only the needle moves. It is a one-pixel
 * standalone model pivoted on the dial's centre, standing a pixel proud of the face, drawn through
 * the item renderer the way the power cell's divider ring is, and turned about the dial's axis by
 * the reading. The needle eases toward its
 * target between syncs so it swings rather than jumps.
 */
public class GasGaugeBlockEntityRenderer implements BlockEntityRenderer<GasGaugeBlockEntity> {

    public static final ModelResourceLocation NEEDLE_MODEL =
            ModelResourceLocation.standalone(ZPSMod.resource("block/gas_gauge/needle"));

    private static final ItemStack NEEDLE_MODEL_STACK = new ItemStack(ModItems.GAS_GAUGE.get());

    /** The dial's sweep in degrees, and where the lower bound sits, clockwise from straight up. */
    public static final float SWEEP_DEGREES = 270.0f;
    public static final float START_DEGREES = -135.0f;

    /**
     * Where the dial face sits along the block's local z axis, measured from the block centre. In
     * the unrotated model the plate is pushed back against the south face and the dial looks
     * north, so the face is {@link GasGaugeBlock#PLATE_THICKNESS} pixels in from the south edge.
     */
    private static final float FACE_OFFSET = (16 - GasGaugeBlock.PLATE_THICKNESS) / 16.0f - 0.5f;
    /** A hair off the face, so the needle's back never fights the dial for the same pixels. */
    private static final float NEEDLE_LIFT = 0.001f;

    /** Fraction of the remaining swing taken up each frame. */
    private static final float SMOOTHING = 0.15f;
    private static final float SETTLED = 0.0005f;

    private final ItemRenderer itemRenderer;

    public GasGaugeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(GasGaugeBlockEntity gauge, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = gauge.getBlockState();
        if (!state.hasProperty(GasGaugeBlock.FACING)) {
            return;
        }

        float target = (float) gauge.getFraction();
        float shown = gauge.getClientNeedleFraction();
        shown += (target - shown) * SMOOTHING;
        if (Math.abs(shown - target) < SETTLED) {
            shown = target;
        }
        gauge.setClientNeedleFraction(shown);

        BakedModel needle = Minecraft.getInstance().getModelManager().getModel(NEEDLE_MODEL);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        applyFacing(poseStack, state.getValue(GasGaugeBlock.FACING));
        poseStack.translate(0.0f, 0.0f, FACE_OFFSET - NEEDLE_LIFT);
        // In the model's frame the dial looks along -z, so a positive turn about +z reads as
        // clockwise to whoever is looking at it.
        poseStack.mulPose(Axis.ZP.rotationDegrees(START_DEGREES + SWEEP_DEGREES * shown));
        itemRenderer.render(NEEDLE_MODEL_STACK, ItemDisplayContext.NONE, false, poseStack, bufferSource,
                packedLight, packedOverlay, needle);
        poseStack.popPose();
    }

    /**
     * Turn the unrotated, north-looking model frame to face the given way. This is the blockstate
     * file's rotation table, expressed the way vanilla applies it: a model rotation of {@code x, y}
     * is a turn of {@code -x} about X followed by {@code -y} about Y.
     */
    private static void applyFacing(PoseStack poseStack, Direction facing) {
        switch (facing) {
            case NORTH -> {}
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(-180));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-270));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case UP -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(-180));
                poseStack.mulPose(Axis.XP.rotationDegrees(-270));
            }
        }
    }
}
