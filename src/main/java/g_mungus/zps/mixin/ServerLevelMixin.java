package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import g_mungus.zps.room.RoomManager;
import g_mungus.zps.world.SkyBiomeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Sky bands where no chunk is loaded: {@code getBiome} falls back to the generator's biome source
 * there, which knows nothing of {@link SkyBiomeSettings}. The client's fallback is {@code ClientLevelMixin}'s.
 *
 * <p>Also reports every block change to {@link RoomManager}. {@code onBlockStateChange} runs for
 * every change whatever its flags, so doors and trapdoors — set without neighbour updates, which
 * {@code NeighborNotifyEvent} misses — open rooms too.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void zps$skyBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> biome = SkyBiomeSettings.skyBiome((ServerLevel) (Object) this, y);
        if (biome != null) cir.setReturnValue(biome);
    }

    /**
     * {@code /locate biome} finds sky biomes. Vanilla asks the generator's biome source, which knows nothing of
     * them and never searches above the build limit; a matching band is straight up or down, so it is found
     * at once, and the generator is then only searched out to it, for anything closer on the ground.
     */
    @WrapMethod(method = "findClosestBiome3d")
    private Pair<BlockPos, Holder<Biome>> zps$locateSkyBiome(Predicate<Holder<Biome>> matches, BlockPos from, int radius,
                                                             int horizontalStep, int verticalStep,
                                                             Operation<Pair<BlockPos, Holder<Biome>>> original) {
        Pair<BlockPos, Holder<Biome>> sky = SkyBiomeSettings.closest((ServerLevel) (Object) this, matches, from);
        if (sky == null) {
            return original.call(matches, from, radius, horizontalStep, verticalStep);
        }
        double skyDistanceSq = sky.getFirst().distSqr(from);
        int within = (int) Math.min(radius, Math.ceil(Math.sqrt(skyDistanceSq)));
        Pair<BlockPos, Holder<Biome>> ground = original.call(matches, from, within, horizontalStep, verticalStep);
        return ground != null && ground.getFirst().distSqr(from) < skyDistanceSq ? ground : sky;
    }

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void zps$roomBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        RoomManager.get(level).onBlockChanged(level, pos, oldState, newState);
    }
}
