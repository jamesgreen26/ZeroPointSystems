package g_mungus.zps.world;

import org.jetbrains.annotations.Nullable;

/** Implemented on {@code Level} by {@code LevelMixin}: the level's {@link SkyBiomeSettings}, looked up once. */
public interface SkyBiomeLevel {
    @Nullable SkyBiomeSettings zps$skyBiomeSettings();
}
