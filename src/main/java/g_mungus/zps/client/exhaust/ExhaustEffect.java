package g_mungus.zps.client.exhaust;

import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.ShaderLightVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * The one Flywheel effect that draws every thruster's exhaust. It belongs to {@link ExhaustParticles}
 * rather than to any block, so particles already in the air outlive the thruster that made them.
 */
final class ExhaustEffect implements Effect {

    private final ClientLevel level;

    ExhaustEffect(ClientLevel level) {
        this.level = level;
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new Visual(ctx, level, partialTick);
    }

    /**
     * One instance per slot of the batch ring's used part (see {@link ExhaustRendering}). Instances
     * are added and removed at the end as the used part grows and shrinks, and are never touched
     * otherwise; slots without a live particle collapse to nothing in the shader.
     */
    private static final class Visual extends AbstractVisual
            implements EffectVisual<ExhaustEffect>, SimpleDynamicVisual, ShaderLightVisual {

        private final List<ExhaustInstance> instances = new ArrayList<>();
        private SectionCollector sections;
        private LongSet trackedSections;

        Visual(VisualizationContext ctx, ClientLevel level, float partialTick) {
            super(ctx, level, partialTick);
        }

        /** Particles are lit from Flywheel's light sections, which only hold what is asked for. */
        @Override
        public void setSectionCollector(SectionCollector sections) {
            this.sections = sections;
            trackedSections = null;
        }

        @Override
        public void beginFrame(DynamicVisual.Context ctx) {
            LongSet tracked = ExhaustParticles.trackedSections();
            if (sections != null && tracked != trackedSections) {
                trackedSections = tracked;
                sections.sections(tracked);
            }
            int wanted = ExhaustParticles.slotsInUse();
            while (instances.size() > wanted) {
                instances.remove(instances.size() - 1).delete();
            }
            if (instances.size() == wanted) {
                return;
            }
            ExhaustInstance[] made = new ExhaustInstance[wanted - instances.size()];
            instancerProvider().instancer(ExhaustRendering.INSTANCE_TYPE, ExhaustRendering.CUBE).createInstances(made);
            for (ExhaustInstance instance : made) {
                instance.slot = instances.size();
                instance.setChanged();
                instances.add(instance);
            }
        }

        @Override
        protected void _delete() {
            for (ExhaustInstance instance : instances) {
                instance.delete();
            }
            instances.clear();
        }
    }
}
