package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link GridPhysics} on a Sable sublevel: that a {@code GridPhysicsActor} is one Sable ticks, and that what the
 * physics view applies and reads back is in the frames it says. Valkyrien Skies is not on the local runtime, so
 * its side is not covered here.
 * <p>
 * Sable is only on the classpath for local runs. Where it is missing the tests pass without doing anything.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GridPhysicsGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    private static final String BATCH = "gridPhysics";

    /** Ticks the push is held for; Sable runs at 20 Hz here, so this is half a second. */
    private static final int PUSH_TICKS = 10;
    /** The push, as an acceleration along the deck's own +x, in blocks per second squared. */
    private static final double ACCELERATION = 20;

    @GameTest(template = TEMPLATE, batch = BATCH + "_actor")
    public static void gridPhysicsActor_isASableActor(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(SableGridPhysicsProbe.actorIsSableActor(),
                "The mixin should make GridPhysicsActor extend Sable's BlockEntitySubLevelActor");
        helper.succeed();
    }

    /**
     * A deck turned a quarter about y and pushed along its own +x through {@link GridPhysics#applyForce} picks up
     * speed along the world's -z, the way {@link GridPhysics#toWorldDirection} says its +x points, at about the
     * acceleration asked for by working the force out from {@link GridPhysics#mass()}.
     */
    @GameTest(template = TEMPLATE, batch = BATCH + "_push", skyAccess = true, timeoutTicks = 200)
    public static void pushAlongTheGridsOwnAxis_movesItThatWayInTheWorld(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                BlockPos pos = new BlockPos(x, 1, z);
                helper.setBlock(pos, Blocks.STONE);
                blocks.add(helper.absolutePos(pos));
            }
        }
        BlockPos min = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos max = helper.absolutePos(new BlockPos(6, 1, 6));

        helper.runAfterDelay(2, () -> {
            // High above the arena, so nothing but the push and gravity acts on it while it is held.
            SableBeamRig rig = SableBeamRig.assemble(level, min, max, blocks, new Quaterniond().rotateY(Math.PI / 2),
                    new Vector3d(min.getX() + 1.5, min.getY() + 60, min.getZ() + 1.5));
            GridPhysics probe = SableGridPhysicsProbe.physicsOf(rig);
            Vec3 worldX = probe.toWorldDirection(new Vec3(1, 0, 0));
            try {
                helper.assertTrue(worldX.distanceTo(new Vec3(0, 0, -1)) < 1e-6,
                        "A quarter turn about y should point the grid's +x along the world's -z, points it along " + worldX);
                helper.assertTrue(probe.toGridDirection(worldX).distanceTo(new Vec3(1, 0, 0)) < 1e-6,
                        "toGridDirection should undo toWorldDirection");
            } catch (RuntimeException e) {
                rig.remove();
                throw e;
            }
            // At the centre of mass, so it only shoves.
            SableGridPhysicsProbe.everySubstep(rig, physics ->
                    physics.applyForce(physics.centreOfMass(), new Vec3(physics.mass() * ACCELERATION, 0, 0)));
            SableGridPhysicsProbe.setPhysicsPaused(level, false);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                SableGridPhysicsProbe.stop();
                GridPhysics physics = SableGridPhysicsProbe.physicsOf(rig);
                Vec3 velocity = physics.linearVelocity();
                Vec3 at = physics.velocityAt(physics.centreOfMass());
                SableGridPhysicsProbe.setPhysicsPaused(level, true);
                rig.remove();
                Vec3 sideways = new Vec3(velocity.x, 0, velocity.z);
                ZPSMod.LOGGER.info("[grid physics] {} blocks/s sideways after {} pushed ticks at {} blocks/s^2 asked; {}",
                        sideways.length(), PUSH_TICKS, ACCELERATION, velocity);
                // Half a second at the acceleration asked for; Sable's drag takes a little off.
                double expected = ACCELERATION * PUSH_TICKS / 20.0;
                helper.assertTrue(Math.abs(sideways.length() - expected) < expected * 0.15, "The deck should have picked up about "
                        + expected + " blocks/s sideways, moves at " + velocity);
                helper.assertTrue(sideways.normalize().dot(new Vec3(0, 0, -1)) > 0.95,
                        "The deck should move along the world's -z, its own +x, moves at " + velocity);
                helper.assertTrue(at.distanceTo(velocity) < 1e-6,
                        "The velocity at the centre of mass should be the linear velocity, is " + at + " against " + velocity);
                helper.succeed();
            });
        });
    }
}
