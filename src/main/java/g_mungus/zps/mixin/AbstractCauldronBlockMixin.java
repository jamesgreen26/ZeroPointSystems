package g_mungus.zps.mixin;

import g_mungus.zps.block.PowderSnowDripstone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A cauldron's scheduled tick is how a stalactite drip lands in it. Vanilla asks the stalactite
 * for its fluid; when the stalactite is fed by powder snow instead, fill with that.
 * See {@link PowderSnowDripstone}.
 */
@Mixin(AbstractCauldronBlock.class)
public class AbstractCauldronBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void zps$receivePowderSnowDrip(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (PowderSnowDripstone.receiveDrip(state, level, pos)) {
            ci.cancel();
        }
    }
}
