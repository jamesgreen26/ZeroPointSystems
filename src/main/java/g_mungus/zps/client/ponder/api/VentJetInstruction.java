package g_mungus.zps.client.ponder.api;

import g_mungus.zps.blockentity.gas.VentBlockEntity;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * A Vent in a ponder scene venting for a while: the scene's stand-in for a particle emitter, drawn
 * by the vent itself.
 *
 * <p>Nothing in a ponder level runs the gas simulation, so the vent's block entity is fed the
 * figures a packet from the server would carry, and its own client ticker draws the jet from them
 * with the gas particle, from the mouth of the plate, at the speed the pressure gives. Non-blocking,
 * like an emitter: the timeline runs on while it vents.
 *
 * <p>The figures are written every tick rather than once, so that two of these overlapping on the
 * same vent behave sensibly: the later one is ticked later and wins while both run, and the earlier
 * one carries on with its own rate once the later has finished. Whichever finishes last leaves the
 * vent quiet.
 */
public class VentJetInstruction extends TickingInstruction {

    /** Kilograms each particle of the jet stands for, as {@code VentBlockEntity} draws it. */
    private static final double MASS_PER_PARTICLE = 0.01;

    private final BlockPos vent;
    private final double ratePerTick;
    private final double pressurePa;
    private final double temperatureK;

    /**
     * @param particlesPerTick how dense a jet to draw, in the units an emitter's rate is given in
     * @param pressurePa       the pressure behind the vent, which sets how fast the jet leaves
     */
    public VentJetInstruction(BlockPos vent, double particlesPerTick, double pressurePa, double temperatureK, int ticks) {
        super(false, ticks);
        this.vent = vent;
        this.ratePerTick = particlesPerTick * MASS_PER_PARTICLE;
        this.pressurePa = pressurePa;
        this.temperatureK = temperatureK;
    }

    @Override
    public void tick(@NotNull PonderScene scene) {
        super.tick(scene);
        // remainingTicks has just been counted down; on the last tick it reaches zero and the vent
        // is left with nothing to draw.
        double rate = isComplete() ? 0 : ratePerTick;
        if (scene.getWorld().getBlockEntity(vent) instanceof VentBlockEntity be) {
            be.acceptSyncedState(rate, isComplete() ? 0 : pressurePa, temperatureK);
        }
    }
}
