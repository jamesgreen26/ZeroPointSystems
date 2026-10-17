package g_mungus.zps.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Light can be read at any height. The light engine looks a position up by {@link BlockPos#asLong}, which
 * holds only 12 bits of height, so anything above y 2047 — high in the sky biomes, well within flying range —
 * wrapped round to the bottom of the world and read as dark: entities up there were drawn black. Clamped
 * into what packs, a position above reads as 2047, already above the top of the world, where vanilla gives
 * full skylight and no block light; one below reads as the bottom of the range.
 */
@Mixin(LightEngine.class)
public abstract class LightEngineMixin {

    @Unique
    private static final int ZPS$MIN_PACKED_Y = -2048;
    @Unique
    private static final int ZPS$MAX_PACKED_Y = 2047;

    @ModifyVariable(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), argsOnly = true)
    private BlockPos zps$packableHeight(BlockPos pos) {
        int y = pos.getY();
        if (y >= ZPS$MIN_PACKED_Y && y <= ZPS$MAX_PACKED_Y) {
            return pos;
        }
        return new BlockPos(pos.getX(), Mth.clamp(y, ZPS$MIN_PACKED_Y, ZPS$MAX_PACKED_Y), pos.getZ());
    }
}
