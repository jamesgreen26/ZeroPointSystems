package g_mungus.zps.client.tractor;

import g_mungus.zps.blockentity.BeamCollectorBlockEntity;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridSpace;
import g_mungus.zps.tractor.BeamForces;
import g_mungus.zps.tractor.BeamGeometry;
import g_mungus.zps.tractor.RunningBeams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * The client's share of a running Tractor Beam, and the only client class the block entity touches.
 * <p>
 * The client moves three things itself. Its own player (or what that player is steering), because the server
 * takes player movement on trust and does not move players at all. And items and falling blocks, because the
 * server only sends their velocity every twentieth tick and they would otherwise lurch. Everything else is the
 * server's to move.
 */
public final class TractorBeamClientHooks {

    private TractorBeamClientHooks() {
    }

    public static void tick(BeamCollectorBlockEntity controller) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        BeamGeometry beam = controller.geometry();
        if (beam.range() <= 0) {
            // Going off: the signal has gone before the word that the beam has. Left signed in at no length, the
            // drawn beam would shrink to nothing instead of fading out where it stood.
            return;
        }
        RunningBeams.Entry entry = RunningBeams.signIn(level, beam, null);

        GridSpace carrier = Compat.gridOf(level, beam.controller());
        LocalPlayer player = Minecraft.getInstance().player;
        Entity steered = player == null ? null : player.getRootVehicle();

        // Wider than the beam, as on the server, for the falling blocks that start out beside it.
        for (Entity entity : level.getEntities((Entity) null,
                beam.worldBounds(level).inflate(BeamForces.LOOSE_BLOCK_SLACK), BeamForces::affects)) {
            boolean mine = entity == steered && (entity == player || entity.isControlledByLocalInstance());
            if (!mine && !BeamForces.isCargo(entity)) {
                continue;
            }
            BeamForces.Grip grip = BeamForces.grip(level, beam, entry.scan(), entity);
            if (grip != null) {
                BeamForces.apply(level, beam, entity, grip, carrier);
            }
        }
    }
}
