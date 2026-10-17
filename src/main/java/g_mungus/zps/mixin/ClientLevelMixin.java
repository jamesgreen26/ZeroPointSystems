package g_mungus.zps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import g_mungus.zps.client.ClientAtmosphere;
import g_mungus.zps.world.AtmosphericComposition;
import g_mungus.zps.world.SkyBiomeSettings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * The sky thins with the air. Its colour darkens by the {@link AtmosphericComposition#getDensity density}
 * where it is seen from, and the weather fades from view as the air thins ({@link ClientAtmosphere#weather}),
 * through the rain level itself: everything the client draws from it — the rain and snow, the grey it
 * puts in the sky and fog, the fading of the sun, moon and stars, thunder's darkening — clears with it.
 * The server's weather is untouched. The stars coming out are {@code LevelRendererMixin}'s.
 * <p>
 * Where no chunk is loaded, sky bands still read their biome rather than the client's plains fallback,
 * so the air and sky stay right with no terrain below.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin extends Level {

    protected ClientLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess,
                               Holder<DimensionType> dimensionType, Supplier<ProfilerFiller> profiler, boolean isClientSide,
                               boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionType, profiler, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 zps$thinSky(Vec3 colour, Vec3 pos, float partialTick) {
        return colour.scale(AtmosphericComposition.getDensity((ClientLevel) (Object) this, BlockPos.containing(pos)));
    }

    @Override
    public float getRainLevel(float partialTick) {
        return super.getRainLevel(partialTick) * ClientAtmosphere.weather((ClientLevel) (Object) this);
    }

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void zps$skyBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> biome = SkyBiomeSettings.skyBiome(this, y);
        if (biome != null) cir.setReturnValue(biome);
    }
}
