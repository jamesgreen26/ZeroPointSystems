package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.gas.ThrusterBlock;
import g_mungus.zps.block.gas.core.GasEdgeNegotiator;
import g_mungus.zps.blockentity.gas.ThrusterBlockEntity;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridPhysics;
import g_mungus.zps.world.AtmosphericComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.ArrayList;
import java.util.List;

/** A thruster on a Sable sublevel in the air, run by a phys tick program. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThrusterSableGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    /** Well after the sink has settled; Rapier would have put the sublevel to sleep by about tick 50. */
    private static final int SETTLED_TICK = 100;
    private static final int LAST_TICK = 300;

    /**
     * A program that throttles by how fast the sublevel falls settles into a slow, steady sink. Slow enough for
     * Rapier to put the sublevel to sleep, which would read its speed as zero, cut the throttle and leave it hanging
     * in the air with no plume; the thruster's push keeps it awake instead, still sinking and still burning.
     * Fed air, which pushes weakly enough that holding the sublevel up takes a good share of the throttle; a fuel as
     * strong as Flux holds it at a few percent, too little sink for the program to read past the step's gravity.
     */
    @GameTest(template = TEMPLATE, batch = "thrusterSable_hover", skyAccess = true, timeoutTicks = 400)
    public static void fallingSpeedProgram_keepsSinkingAndBurning(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                BlockPos pos = new BlockPos(x, 3, z);
                if (x == 5 && z == 5) {
                    helper.setBlock(pos, ModBlocks.THRUSTER.get().defaultBlockState()
                            .setValue(ThrusterBlock.FACING, Direction.DOWN).setValue(ThrusterBlock.POWER, 0));
                } else {
                    helper.setBlock(pos, Blocks.STONE);
                }
                blocks.add(helper.absolutePos(pos));
            }
        }
        BlockPos min = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos max = helper.absolutePos(new BlockPos(6, 3, 6));
        SableBeamRig[] rig = {null};
        ThrusterBlockEntity[] thruster = {null};
        int[] tick = {-3};
        double[] earlyY = {0};
        String command = "thrust value_of(ship world_vel dot 0 -1 0)";
        helper.onEachTick(() -> {
            int at = tick[0]++;
            if (at == -1) {
                rig[0] = SableBeamRig.assemble(level, min, max, blocks, new Quaterniond(),
                        new Vector3d(min.getX() + 1.5, min.getY() + 40, min.getZ() + 1.5));
                for (BlockPos pos : rig[0].blocksLeft()) {
                    if (level.getBlockEntity(pos) instanceof ThrusterBlockEntity found) {
                        thruster[0] = found;
                    }
                }
                SableGridPhysicsProbe.setPhysicsPaused(level, false);
                return;
            }
            if (at < 0 || at > LAST_TICK || thruster[0] == null) {
                return;
            }
            ThrusterBlockEntity t = thruster[0];
            GasType air = GasTypeRegistry.INSTANCE.getGasType(AtmosphericComposition.AIR);
            KelvinMod.INSTANCE.forceGetKelvin().addGasAtTemperature(
                    GasEdgeNegotiator.nodePos(level, t.getBlockPos()), air, air.molesToMass(1), 300.0);
            t.physTickProgram().accept(command, level.getServer().createCommandSourceStack().withLevel(level),
                    level, t.getBlockPos());
            GridPhysics physics = SableGridPhysicsProbe.physicsOf(rig[0]);
            if (at == SETTLED_TICK) {
                earlyY[0] = physics.position().y;
            }
            if (at == LAST_TICK) {
                double sunk = earlyY[0] - physics.position().y;
                double throttle = t.throttle();
                float burn = t.burn();
                SableGridPhysicsProbe.setPhysicsPaused(level, true);
                rig[0].remove();
                helper.assertTrue(sunk > 0.5, "The sublevel should still be sinking under its thrust, sank " + sunk
                        + " from tick " + SETTLED_TICK + " to " + LAST_TICK);
                helper.assertTrue(throttle > 0 && burn > 0, "The program should still be burning, throttle " + throttle
                        + ", burn " + burn);
                helper.succeed();
            }
        });
    }

    /** Sent the way a Serial Bus sends it, a program with a plain throttle runs on the sublevel. */
    @GameTest(template = TEMPLATE, batch = "thrusterSable_sent", skyAccess = true, timeoutTicks = 200)
    public static void sentProgram_runs(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                BlockPos pos = new BlockPos(x, 3, z);
                if (x == 5 && z == 5) {
                    helper.setBlock(pos, ModBlocks.THRUSTER.get().defaultBlockState()
                            .setValue(ThrusterBlock.FACING, Direction.DOWN).setValue(ThrusterBlock.POWER, 0));
                } else {
                    helper.setBlock(pos, Blocks.STONE);
                }
                blocks.add(helper.absolutePos(pos));
            }
        }
        BlockPos min = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos max = helper.absolutePos(new BlockPos(6, 3, 6));
        SableBeamRig[] rig = {null};
        ThrusterBlockEntity[] thruster = {null};
        int[] tick = {-3};
        helper.onEachTick(() -> {
            int at = tick[0]++;
            if (at == -1) {
                rig[0] = SableBeamRig.assemble(level, min, max, blocks, new Quaterniond(),
                        new Vector3d(min.getX() + 1.5, min.getY() + 40, min.getZ() + 1.5));
                for (BlockPos pos : rig[0].blocksLeft()) {
                    if (level.getBlockEntity(pos) instanceof ThrusterBlockEntity found) {
                        thruster[0] = found;
                    }
                }
                SableGridPhysicsProbe.setPhysicsPaused(level, false);
                return;
            }
            if (at < 0 || at > 40 || thruster[0] == null) {
                return;
            }
            ThrusterBlockEntity t = thruster[0];
            ScriptCommandFailure failure = ZPSScripts.get().tryRun("on_phys_tick command(thrust 0.5)",
                    level.getServer().createCommandSourceStack().withLevel(level), t.getBlockPos());
            if (at == 40) {
                double throttle = t.throttle();
                boolean running = t.physTickProgram().isRunning();
                SableGridPhysicsProbe.setPhysicsPaused(level, true);
                rig[0].remove();
                helper.assertTrue(failure == null, "Sending the program should not fail, got " + failure);
                helper.assertTrue(running, "The program should be running");
                helper.assertTrue(throttle == 0.5, "The program should set the throttle, is " + throttle);
                helper.succeed();
            }
        });
    }
}
