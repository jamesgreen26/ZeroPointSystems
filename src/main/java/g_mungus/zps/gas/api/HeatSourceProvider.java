package g_mungus.zps.gas.api;

import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * Supplies the {@link HeatSource}s in a level, registered with {@link HeatSources#register}.
 *
 * <p>Asked every time an atmosphere's temperature is read in a {@code zps:space} biome: on both sides,
 * often (every entity every tick, rooms, random ticks), so it must be cheap. Positions are world
 * coordinates in {@code level}. Return nothing for levels it has no sources in.
 */
@FunctionalInterface
public interface HeatSourceProvider {
    Collection<HeatSource> sources(Level level);
}
