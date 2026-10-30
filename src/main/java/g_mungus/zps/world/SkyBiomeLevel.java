package g_mungus.zps.world;

import org.jetbrains.annotations.Nullable;

/** Implemented on {@code Level} by {@code LevelMixin}: the level's {@link SkyBiomeSettings}, looked up and resolved once. */
public interface SkyBiomeLevel {
    @Nullable SkyBiomeSettings.Resolved zps$skyBiomeSettings();
}
