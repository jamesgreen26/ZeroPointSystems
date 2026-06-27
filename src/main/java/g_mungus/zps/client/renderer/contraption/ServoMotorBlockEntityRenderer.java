package g_mungus.zps.client.renderer.contraption;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Vanilla fallback renderer for a Servo Motor's contraption, used when the
 * Flywheel backend is unavailable. Renders each captured block with the live
 * rotation applied around the motor's facing axis.
 */
public class ServoMotorBlockEntityRenderer implements BlockEntityRenderer<ServoMotorBlockEntity> {

	public ServoMotorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(ServoMotorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
		int packedLight, int packedOverlay) {
		Contraption contraption = be.getContraption();
		if (contraption == null || contraption.isEmpty())
			return;

		float angle = be.getInterpolatedAngle(partialTick);
		Direction facing = be.getFacing();

		poseStack.pushPose();
		// BER origin is the motor block corner; shift to the anchor block and pivot there.
		poseStack.translate(facing.getStepX(), facing.getStepY(), facing.getStepZ());
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(axisRotation(facing.getAxis(), angle));
		poseStack.translate(-0.5, -0.5, -0.5);

		BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
		for (StructureBlockInfo info : contraption.getBlocks().values()) {
			BlockPos local = info.pos();
			poseStack.pushPose();
			poseStack.translate(local.getX(), local.getY(), local.getZ());
			dispatcher.renderSingleBlock(info.state(), poseStack, buffers, packedLight, packedOverlay,
				ModelData.EMPTY, null);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static org.joml.Quaternionf axisRotation(Direction.Axis axis, float degrees) {
		return switch (axis) {
			case X -> Axis.XP.rotationDegrees(degrees);
			case Y -> Axis.YP.rotationDegrees(degrees);
			case Z -> Axis.ZP.rotationDegrees(degrees);
		};
	}

	@Override
	public boolean shouldRenderOffScreen(ServoMotorBlockEntity be) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
