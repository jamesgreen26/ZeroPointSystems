package g_mungus.zps.client.renderer.contraption;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import g_mungus.zps.blockentity.ServoMotorBlockEntity;
import g_mungus.zps.contraption.Contraption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
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
		float angle = be.getInterpolatedAngle(partialTick);
		Direction facing = be.getFacing();
		boolean flywheelActive = be.getLevel() != null && VisualizationManager.supportsVisualization(be.getLevel());

		poseStack.pushPose();
		// BER origin is the motor block corner; shift to the anchor block and pivot there.
		poseStack.translate(facing.getStepX(), facing.getStepY(), facing.getStepZ());
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(axisRotation(facing.getAxis(), angle));
		poseStack.translate(-0.5, -0.5, -0.5);

		// The head's own block is invisible; draw the piston-head model directly off the motor's
		// facing so it appears immediately on placement (no wait for the contraption to sync) and
		// in both render backends (this BER always runs via neverSkipVanillaRender).
		renderHead(facing, poseStack, buffers, packedLight, packedOverlay);

		Contraption contraption = be.getContraption();
		if (contraption != null && !contraption.isEmpty()) {
			// Static block models: only when Flywheel isn't drawing the structure instance. (The
			// invisible head produces no geometry here either way.)
			if (!flywheelActive) {
				BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
				for (StructureBlockInfo info : contraption.getBlocks().values()) {
					BlockPos local = info.pos();
					poseStack.pushPose();
					poseStack.translate(local.getX(), local.getY(), local.getZ());
					dispatcher.renderSingleBlock(info.state(), poseStack, buffers, packedLight, packedOverlay,
						ModelData.EMPTY, null);
					poseStack.popPose();
				}
			}

			// Captured block entities (chests, signs, ...): rendered via their vanilla
			// renderers so they keep rendering while assembled. A block entity is skipped
			// only when Flywheel is active AND it opts out of vanilla rendering — those are
			// drawn by their Flywheel child visual in ServoMotorVisual instead.
			renderCapturedBlockEntities(be, partialTick, poseStack, buffers, packedLight, packedOverlay, flywheelActive);
		}

		poseStack.popPose();
	}

	/** Draw the vanilla piston-head model at the motor's own cell (the head's local position). */
	private static void renderHead(Direction facing, PoseStack poseStack, MultiBufferSource buffers, int packedLight,
		int packedOverlay) {
		BlockState head = Blocks.PISTON_HEAD.defaultBlockState()
			.setValue(PistonHeadBlock.FACING, facing)
			.setValue(PistonHeadBlock.TYPE, PistonType.DEFAULT)
			.setValue(PistonHeadBlock.SHORT, false);
		BlockPos local = BlockPos.ZERO.relative(facing.getOpposite());
		poseStack.pushPose();
		poseStack.translate(local.getX(), local.getY(), local.getZ());
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(head, poseStack, buffers, packedLight,
			packedOverlay, ModelData.EMPTY, null);
		poseStack.popPose();
	}

	private static void renderCapturedBlockEntities(ServoMotorBlockEntity be, float partialTick, PoseStack poseStack,
		MultiBufferSource buffers, int packedLight, int packedOverlay, boolean flywheelActive) {
		ContraptionRenderState renderState = be.getRenderState();
		if (renderState == null)
			return;

		BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
		for (BlockEntity captured : renderState.getBlockEntities()) {
			if (flywheelActive && VisualizationHelper.skipVanillaRender(captured))
				continue;
			BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(captured);
			if (renderer == null)
				continue;
			BlockPos local = captured.getBlockPos();
			poseStack.pushPose();
			poseStack.translate(local.getX(), local.getY(), local.getZ());
			try {
				renderer.render(captured, partialTick, poseStack, buffers, packedLight, packedOverlay);
			} catch (Exception ignored) {
				// A misbehaving virtual render shouldn't crash the frame.
			}
			poseStack.popPose();
		}
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
