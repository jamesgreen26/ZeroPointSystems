package g_mungus.zps.client.ponder.api;

import g_mungus.zps.client.renderer.RoboticArmBlockEntityRenderer;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;

/**
 * Drives the Robotic Arm's in-game range visualization (the cyan checkered reach cluster)
 * onto the Ponder scene's own outliner, so it renders in scene space rather than the world.
 * Reuses {@link RoboticArmBlockEntityRenderer#showRangeOutline}, refreshing it each tick for
 * the given duration.
 */
public class RoboticArmRangeInstruction extends TickingInstruction {

    private final BlockPos origin;

    public RoboticArmRangeInstruction(BlockPos origin, int ticks) {
        super(false, ticks);
        this.origin = origin;
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        RoboticArmBlockEntityRenderer.showRangeOutline(scene.getOutliner(), origin);
    }
}
