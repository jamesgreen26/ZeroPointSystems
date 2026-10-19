package g_mungus.zps.mixin;

import g_mungus.zps.client.exhaust.ExhaustParticles;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every block change the client sees, so the terrain exhaust collides with keeps up. */
@Mixin(LevelRenderer.class)
public abstract class ExhaustBlockChangeMixin {

    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void zps$exhaustTerrain(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState,
                                    int flags, CallbackInfo ci) {
        ExhaustParticles.onBlockChanged(pos, newState);
    }
}
