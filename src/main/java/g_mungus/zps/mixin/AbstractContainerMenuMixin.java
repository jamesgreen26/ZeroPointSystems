package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import g_mungus.zps.contraption.ContraptionSimServerLevel;
import g_mungus.zps.contraption.ContraptionTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Companion to {@link ContainerMixin} for the other standard reach helper. {@code ServerPlayer#tick}
 * closes a menu whose {@link AbstractContainerMenu#stillValid(Player)} returns false; the common
 * {@code stillValid(ContainerLevelAccess, Player, Block)} helper measures the 8-block distance to the
 * block's position, but a block entity riding a contraption sits at contraption-LOCAL coordinates in
 * {@link ContraptionSimServerLevel}, so the check fails the instant the GUI opens and the menu
 * vanishes. When the lookup level is a contraption sim level, redo the distance test against the
 * block's actual rotated WORLD position. Fixing it in the shared helper means every menu-backed block
 * works on a contraption with no per-menu changes.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

	@Inject(method = "lambda$stillValid$0(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ljava/lang/Boolean;",
		at = @At("HEAD"), cancellable = true)
	private static void zps$contraptionStillValid(Block block, Player player, Level level, BlockPos pos,
		CallbackInfoReturnable<Boolean> cir) {
		if (!(level instanceof ContraptionSimServerLevel sim))
			return;
		if (!level.getBlockState(pos).is(block)) {
			cir.setReturnValue(false);
			return;
		}
		ContraptionTransform transform = sim.getTransform();
		if (transform == null) {
			// No pose available (e.g. tests): block identity already checked, accept.
			cir.setReturnValue(true);
			return;
		}
		// Mirror vanilla's check (distance from the player to the block centre <= 8 blocks), but at
		// the block's rotated world position rather than its contraption-local coordinates.
		Vec3 worldCenter = transform.localBlockCenterToWorld(pos);
		cir.setReturnValue(player.distanceToSqr(worldCenter.x, worldCenter.y, worldCenter.z) <= 64.0);
	}
}
