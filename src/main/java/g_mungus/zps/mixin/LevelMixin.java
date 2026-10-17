package g_mungus.zps.mixin;

import g_mungus.zps.world.SkyBiomeLevel;
import g_mungus.zps.world.SkyBiomeSettings;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Caches the level's {@link SkyBiomeSettings}. Datapack registries are fixed for the life of a
 * server (and of a client connection), so one lookup per level is enough. Worldgen threads may race
 * to fill the cache; they all compute the same value.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements SkyBiomeLevel {
    @Unique
    private @Nullable SkyBiomeSettings zps$skyBiomeSettings;
    /** Written after the settings, so a thread that sees it set also sees them. */
    @Unique
    private volatile boolean zps$skyBiomeSettingsLoaded;

    @Override
    public @Nullable SkyBiomeSettings zps$skyBiomeSettings() {
        if (!zps$skyBiomeSettingsLoaded) {
            Level level = (Level) (Object) this;
            zps$skyBiomeSettings = SkyBiomeSettings.forDimension(level.registryAccess(), level.dimension()).orElse(null);
            zps$skyBiomeSettingsLoaded = true;
        }
        return zps$skyBiomeSettings;
    }
}
