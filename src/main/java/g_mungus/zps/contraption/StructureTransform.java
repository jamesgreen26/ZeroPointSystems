package g_mungus.zps.contraption;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

import g_mungus.zps.contraption.util.ContraptionMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * Converts contraption-local positions/states back into world space on
 * disassembly, snapping the live rotation to the nearest quarter turn.
 * Clean-room reimplementation of Create's StructureTransform: the block-state
 * rotation cases below are pure-vanilla logic; the Create transformer registry
 * and mirror/network plumbing are intentionally omitted.
 */
public class StructureTransform {

	// Assuming structures cannot be rotated around multiple axes at once.
	public final Axis rotationAxis;
	public final BlockPos offset;
	public int angle;
	public Rotation rotation;

	public StructureTransform(BlockPos offset, Axis axis, float rotationDegrees) {
		this.offset = offset;
		this.rotationAxis = axis;

		angle = Math.round(rotationDegrees / 90) * 90;
		angle %= 360;
		if (angle < -90)
			angle += 360;

		rotation = Rotation.NONE;
		if (angle == -90 || angle == 270)
			rotation = Rotation.CLOCKWISE_90;
		if (angle == 90)
			rotation = Rotation.COUNTERCLOCKWISE_90;
		if (angle == 180)
			rotation = Rotation.CLOCKWISE_180;
	}

	public Vec3 applyWithoutOffset(Vec3 localVec) {
		if (rotationAxis == null)
			return localVec;
		return ContraptionMath.rotateCentered(localVec, angle, rotationAxis);
	}

	public Vec3 apply(Vec3 localVec) {
		return applyWithoutOffset(localVec).add(Vec3.atLowerCornerOf(offset));
	}

	public BlockPos apply(BlockPos localPos) {
		return BlockPos.containing(applyWithoutOffset(ContraptionMath.getCenterOf(localPos))).offset(offset);
	}

	/**
	 * Vanilla only supports block-state rotation around Y. The horizontal-axis
	 * cases mirror Create's handling for common vanilla blocks.
	 */
	public BlockState apply(BlockState state) {
		var block = state.getBlock();

		if (rotationAxis == Axis.Y)
			return state.rotate(rotation);

		if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
			DirectionProperty facingProperty = FaceAttachedHorizontalDirectionalBlock.FACING;
			EnumProperty<AttachFace> faceProperty = FaceAttachedHorizontalDirectionalBlock.FACE;
			Direction stateFacing = state.getValue(facingProperty);
			AttachFace stateFace = state.getValue(faceProperty);
			boolean z = rotationAxis == Axis.Z;
			Direction forcedAxis = z ? Direction.WEST : Direction.SOUTH;

			if (stateFacing.getAxis() == rotationAxis && stateFace == AttachFace.WALL)
				return state;

			for (int i = 0; i < rotation.ordinal(); i++) {
				stateFace = state.getValue(faceProperty);
				stateFacing = state.getValue(facingProperty);

				boolean b = state.getValue(faceProperty) == AttachFace.CEILING;
				state = state.setValue(facingProperty, b ? forcedAxis : forcedAxis.getOpposite());

				if (stateFace != AttachFace.WALL) {
					state = state.setValue(faceProperty, AttachFace.WALL);
					continue;
				}

				if (stateFacing.getAxisDirection() == (z ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE)) {
					state = state.setValue(faceProperty, AttachFace.FLOOR);
					continue;
				}
				state = state.setValue(faceProperty, AttachFace.CEILING);
			}

			return state;
		}

		boolean halfTurn = rotation == Rotation.CLOCKWISE_180;
		if (block instanceof StairBlock)
			return transformStairs(state, halfTurn);

		if (state.hasProperty(FACING)) {
			state = state.setValue(FACING, rotateFacing(state.getValue(FACING)));
		} else if (state.hasProperty(AXIS)) {
			state = state.setValue(AXIS, rotateAxis(state.getValue(AXIS)));
		} else if (halfTurn) {
			if (state.hasProperty(HORIZONTAL_FACING)) {
				Direction stateFacing = state.getValue(HORIZONTAL_FACING);
				if (stateFacing.getAxis() == rotationAxis)
					return state;
			}

			state = state.rotate(rotation);

			if (state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE)
				state = state.setValue(SlabBlock.TYPE,
					state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? SlabType.TOP : SlabType.BOTTOM);
		}

		return state;
	}

	protected BlockState transformStairs(BlockState state, boolean halfTurn) {
		if (state.getValue(StairBlock.FACING).getAxis() != rotationAxis) {
			for (int i = 0; i < rotation.ordinal(); i++) {
				Direction direction = state.getValue(StairBlock.FACING);
				Half half = state.getValue(StairBlock.HALF);
				if (direction.getAxisDirection() == AxisDirection.POSITIVE ^ half == Half.BOTTOM
					^ direction.getAxis() == Axis.Z)
					state = state.cycle(StairBlock.HALF);
				else
					state = state.setValue(StairBlock.FACING, direction.getOpposite());
			}
		} else if (halfTurn) {
			state = state.cycle(StairBlock.HALF);
		}
		return state;
	}

	public Axis rotateAxis(Axis axis) {
		Direction facing = Direction.get(AxisDirection.POSITIVE, axis);
		return rotateFacing(facing).getAxis();
	}

	public Direction rotateFacing(Direction facing) {
		for (int i = 0; i < rotation.ordinal(); i++)
			facing = facing.getClockWise(rotationAxis);
		return facing;
	}
}
