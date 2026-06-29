package g_mungus.zps.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import g_mungus.zps.contraption.ContraptionMenuContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client-side: while a menu is being constructed for a block on a contraption, redirect the menu
 * factory's {@code level.getBlockEntity(localPos)} lookup to the contraption's live client block
 * entity, so any menu-backed block can open its GUI on a contraption without per-block patching.
 * Outside that window {@link ContraptionMenuContext#resolve} returns {@code null} and the lookup is
 * untouched. See {@link ContraptionMenuContext}.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

	@Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
		at = @At("HEAD"), cancellable = true)
	private void zps$redirectContraptionMenuLookup(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
		Level self = (Level) (Object) this;
		if (!self.isClientSide)
			return;
		BlockEntity be = ContraptionMenuContext.resolve(self, pos);
		if (be != null)
			cir.setReturnValue(be);
	}
}
