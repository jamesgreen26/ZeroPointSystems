package g_mungus.zps.mixin;

import g_mungus.zps.gas.BlockGasContact;
import g_mungus.zps.gas.GasExposure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ice with gas against any face hot enough to set a player alight ({@link GasExposure#isBurning})
 * melts on its random tick, as it would under a bright light. See {@link BlockGasContact}.
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {

    @Shadow
    protected abstract void melt(BlockState state, Level level, BlockPos pos);

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void zps$meltInHotGas(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        for (BlockGasContact.Contact contact : BlockGasContact.contacts(level, pos)) {
            if (GasExposure.isBurning(contact.temperatureK())) {
                melt(state, level, pos);
                ci.cancel();
                return;
            }
        }
    }
}
