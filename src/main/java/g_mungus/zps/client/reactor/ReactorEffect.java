package g_mungus.zps.client.reactor;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelAccessor;

/**
 * The reactor glow as a Flywheel effect: a visual that belongs to no block or entity. Flywheel
 * keys effects by identity, so each {@link ClientReactor} owns exactly one of these for its life.
 */
public final class ReactorEffect implements Effect {

    private final ClientLevel level;
    private final ClientReactor reactor;

    ReactorEffect(ClientLevel level, ClientReactor reactor) {
        this.level = level;
        this.reactor = reactor;
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new ReactorEffectVisual(ctx, level, reactor, partialTick);
    }
}
