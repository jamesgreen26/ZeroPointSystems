package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import g_mungus.zps.contraption.ContraptionSimServerLevel;
import g_mungus.zps.contraption.ContraptionTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Lets a player keep a container GUI open while interacting with a block entity that lives on a
 * contraption. Vanilla's {@link Container#stillValidBlockEntity} reach check measures the distance
 * to the block entity's position — but contraption block entities sit at contraption-LOCAL
 * coordinates in {@link ContraptionSimServerLevel}, so the raw check fails the instant the GUI
 * opens. When the BE belongs to a contraption sim level, redo the check against the block's actual
 * rotated WORLD position (via {@link ContraptionTransform}), keeping the identity check intact.
 */
@Mixin(Container.class)
public interface ContainerMixin {

	@Inject(
		method = "stillValidBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;F)Z",
		at = @At("HEAD"), cancellable = true)
	private static void zps$contraptionStillValid(BlockEntity blockEntity, Player player, float distance,
		CallbackInfoReturnable<Boolean> cir) {
		Level level = blockEntity.getLevel();
		if (!(level instanceof ContraptionSimServerLevel sim))
			return;
		BlockPos local = blockEntity.getBlockPos();
		if (sim.getBlockEntity(local) != blockEntity) {
			cir.setReturnValue(false);
			return;
		}
		ContraptionTransform transform = sim.getTransform();
		if (transform == null) {
			// No pose available (e.g. tests): identity already checked, accept.
			cir.setReturnValue(true);
			return;
		}
		Vec3 worldCenter = transform.localBlockCenterToWorld(local);
		double reach = player.blockInteractionRange() + distance;
		cir.setReturnValue(worldCenter.distanceToSqr(player.getEyePosition()) < reach * reach);
	}
}
