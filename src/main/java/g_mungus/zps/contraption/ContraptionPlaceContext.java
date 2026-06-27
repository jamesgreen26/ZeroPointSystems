package g_mungus.zps.contraption;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

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
		return transform.worldDirToLocal(super.getNearestLookingVerticalDirection());
	}

	@Override
	public Direction getHorizontalDirection() {
		return transform.worldDirToLocal(super.getHorizontalDirection());
	}

	@Override
	public float getRotation() {
		// Player-relative yaw (banners, signs, skulls) measured in the structure's frame.
		float rotation = super.getRotation();
		return transform.axis() == Direction.Axis.Y ? rotation - transform.angle() : rotation;
	}
}
