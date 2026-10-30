package g_mungus.zps.mixin;

import g_mungus.zps.gas.BlockGasContact;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Farmland with steam against any face is as good as beside water: its random tick keeps it
 * hydrated. See {@link BlockGasContact}.
 */
@Mixin(FarmBlock.class)
public class FarmBlockMixin {

    @Inject(method = "isNearWater", at = @At("HEAD"), cancellable = true)
    private static void zps$hydrateFromSteam(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ServerLevel server && BlockGasContact.touches(server, pos, ModGases.STEAM.getResourceLocation())) {
            cir.setReturnValue(true);
        }
    }
}
