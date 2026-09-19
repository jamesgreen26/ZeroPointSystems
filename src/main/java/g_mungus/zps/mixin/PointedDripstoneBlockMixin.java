package g_mungus.zps.mixin;

import g_mungus.zps.block.PowderSnowDripstone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets powder snow above a stalactite's support block drip into cauldrons, the way water and
 * lava do. See {@link PowderSnowDripstone}. Both hooks hand off to vanilla untouched unless the
 * source block is powder snow.
 */
@Mixin(PointedDripstoneBlock.class)
public class PointedDripstoneBlockMixin {

    /** The random-tick entry point; vanilla reads the source as a fluid, which powder snow is not. */
    @Inject(method = "maybeTransferFluid", at = @At("HEAD"), cancellable = true)
    private static void zps$dripPowderSnow(BlockState state, ServerLevel level, BlockPos pos, float randChance, CallbackInfo ci) {
        if (PowderSnowDripstone.maybeDrip(state, level, pos, randChance)) {
            ci.cancel();
        }
    }

    /** Ambient drip particles: snowflakes at the cauldron-filling rate instead of a rare water drip. */
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void zps$animatePowderSnowDrip(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (PowderSnowDripstone.animateDrip(state, level, pos, random)) {
            ci.cancel();
        }
    }
}
