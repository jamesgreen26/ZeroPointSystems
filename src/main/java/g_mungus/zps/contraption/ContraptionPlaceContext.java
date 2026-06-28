package g_mungus.zps.contraption;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A {@link BlockPlaceContext} for placing into a contraption. The clicked
 * face/pos/location come from a synthetic hit result already in contraption-LOCAL
 * space, but the player-derived look directions are world space, so they are
 * rotated into local space here. This makes {@code getStateForPlacement} orient
 * directional blocks (stairs, logs, pistons…) correctly relative to the rotating
 * structure while neighbour-aware blocks (fences, walls, redstone) see the
 * contraption's own blocks via the simulation level.
 */
public class ContraptionPlaceContext extends BlockPlaceContext {

	private final ContraptionTransform transform;

	public ContraptionPlaceContext(Level simLevel, Player player, InteractionHand hand, ItemStack stack,
		BlockHitResult localHit, ContraptionTransform transform) {
		super(simLevel, player, hand, stack, localHit);
		this.transform = transform;
	}

	/** Result of resolving a placement: the local position and the block state to put there. */
	public record Placed(BlockPos pos, BlockState state) {}

	/**
	 * Run vanilla placement against a level that exposes the contraption's blocks
	 * (so neighbour-aware states connect) and return where/what to place, or null if
	 * the placement is invalid. Shared by the authoritative server path and the
	 * client-side prediction so both compute the same state.
	 */
	@Nullable
	public static Placed resolve(Level simLevel, Player player, InteractionHand hand, ItemStack stack,
		BlockPos local, Direction face, Vec3 hit, ContraptionTransform transform) {
		if (!(stack.getItem() instanceof BlockItem blockItem))
			return null;
		BlockHitResult localHit = new BlockHitResult(hit, face, local, false);
		ContraptionPlaceContext ctx = new ContraptionPlaceContext(simLevel, player, hand, stack, localHit, transform);
		if (!ctx.canPlace())
			return null;
		BlockPos pos = ctx.getClickedPos();
		BlockState state = blockItem.getBlock().getStateForPlacement(ctx);
		if (state == null || !state.canSurvive(simLevel, pos))
			return null;
		return new Placed(pos, state);
	}

	@Override
	public Direction[] getNearestLookingDirections() {
		Direction[] world = Direction.orderedByNearest(getPlayer());
		Direction[] local = new Direction[world.length];
		for (int i = 0; i < world.length; i++)
			local[i] = transform.worldDirToLocal(world[i]);

		if (replaceClicked)
			return local;

		Direction opposite = getClickedFace().getOpposite();
		int i = 0;
		while (i < local.length && local[i] != opposite)
			++i;
		if (i > 0 && i < local.length) {
			System.arraycopy(local, 0, local, 1, i);
			local[0] = opposite;
		}
		return local;
	}

	@Override
	public Direction getNearestLookingDirection() {
		return getNearestLookingDirections()[0];
	}

	@Override
	public Direction getNearestLookingVerticalDirection() {
		for (Direction world : Direction.orderedByNearest(getPlayer())) {
			Direction local = transform.worldDirToLocal(world);
			if (local.getAxis() == Direction.Axis.Y)
				return local;
		}
		return super.getNearestLookingVerticalDirection();
	}

	@Override
	public Direction getHorizontalDirection() {
		for (Direction world : Direction.orderedByNearest(getPlayer())) {
			Direction local = transform.worldDirToLocal(world);
			if (local.getAxis() != Direction.Axis.Y)
				return local;
		}
		return super.getHorizontalDirection();
	}

	@Override
	public float getRotation() {
		// Player-relative yaw (banners, signs, skulls) measured in the structure's frame.
		float rotation = super.getRotation();
		return transform.axis() == Direction.Axis.Y ? rotation - transform.angle() : rotation;
	}
}
