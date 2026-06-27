package g_mungus.zps.contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides which blocks may be picked up into a contraption and how they should
 * be ordered on disassembly. Deliberately permissive for a first version: any
 * non-air, breakable block is movable, and "brittle" blocks (non-full collision
 * shape, e.g. torches) are placed last so their supports exist first.
 */
public final class BlockMovementChecks {

	private BlockMovementChecks() {}

	/** Whether this block is worth capturing at all (not air / not replaceable nothing). */
	public static boolean isMovementNecessary(BlockState state) {
		return !state.isAir();
	}

	/** Whether this block is allowed to move (not unbreakable like bedrock). */
	public static boolean isMovementAllowed(BlockState state, Level level, BlockPos pos) {
		if (state.isAir())
			return false;
		// Unbreakable blocks report a negative destroy speed.
		return state.getDestroySpeed(level, pos) >= 0;
	}

	/** Brittle blocks are placed after solid ones during disassembly. */
	public static boolean isBrittle(BlockState state, Level level, BlockPos pos) {
		return !state.isCollisionShapeFullBlock(level, pos);
	}
}
