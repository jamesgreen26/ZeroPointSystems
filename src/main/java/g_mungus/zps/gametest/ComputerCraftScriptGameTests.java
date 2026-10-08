package g_mungus.zps.gametest;

import dan200.computercraft.shared.ModRegistry;
import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.compat.Compat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.List;

/**
 * Script commands made from ComputerCraft peripherals. ComputerCraft is only on the classpath for
 * local runs; without it these pass without doing anything.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ComputerCraftScriptGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos TARGET = new BlockPos(3, 1, 3);

    /** Every inventory has size, which says little and would crowd the suggestions, so it is no getter. */
    @GameTest(template = TEMPLATE)
    public static void inventorySizeIsNoGetter(GameTestHelper helper) {
        if (!Compat.isComputerCraftLoaded()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(!ZPSScripts.get().hasGetter("size"), "size should not be a getter");
        helper.succeed();
    }

    /** ComputerCraft's energy methods reach ZPS's own blocks too, named without "get". */
    @GameTest(template = TEMPLATE)
    public static void powerCellEnergyCapacityIsAGetter(GameTestHelper helper) {
        if (!Compat.isComputerCraftLoaded()) {
            helper.succeed();
            return;
        }
        helper.setBlock(TARGET, ModBlocks.POWER_CELL.get());
        BlockPos cell = helper.absolutePos(TARGET);
        Integer capacity = evaluate(helper, cell, "energy_capacity", BuiltInTypes.INT);
        helper.assertTrue(capacity != null && capacity > 0, "energy_capacity should read the cell's capacity, got " + capacity);
        helper.assertTrue(evaluate(helper, cell, "energy", BuiltInTypes.INT) == 0, "An empty cell's energy should read 0");
        helper.succeed();
    }

    /** A method taking one number is an executor: a monitor's text scale, set and read back. */
    @GameTest(template = TEMPLATE)
    public static void monitorTextScaleIsSetAndRead(GameTestHelper helper) {
        if (!Compat.isComputerCraftLoaded()) {
            helper.succeed();
            return;
        }
        helper.setBlock(TARGET, ModRegistry.Blocks.MONITOR_ADVANCED.get());
        helper.runAfterDelay(2, () -> {
            BlockPos monitor = helper.absolutePos(TARGET);
            ScriptCommandFailure failure = ZPSScripts.get().tryRun("set_text_scale 2", source(helper), monitor);
            helper.assertTrue(failure == null, "set_text_scale failed: " + failure);
            Double scale = evaluate(helper, monitor, "text_scale", BuiltInTypes.DOUBLE);
            helper.assertTrue(scale == 2.0, "text_scale should read the 2 it was set to, got " + scale);
            helper.succeed();
        });
    }

    /**
     * Computer APIs come through too, answering about where the command is aimed: CC: Sable's
     * sublevel API, about a block lifted onto a sublevel. Its own batch, since a sublevel pauses
     * physics for the whole level while it stands.
     */
    @GameTest(template = TEMPLATE, batch = "computerCraftSable", timeoutTicks = 100)
    public static void sublevelApiAnswersAboutTheTarget(GameTestHelper helper) {
        if (!Compat.isComputerCraftLoaded() || !Compat.isSableLoaded() || !ModList.get().isLoaded("cc_sable")) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos lifted = helper.absolutePos(TARGET);
        helper.runAfterDelay(2, () -> {
            SableBeamRig rig = SableBeamRig.assemble(level, lifted, lifted, List.of(lifted), new Quaterniond());
            try {
                BlockPos onPlot = rig.blocksLeft().get(0);
                Double mass = evaluate(helper, onPlot, "sublevel_mass", BuiltInTypes.DOUBLE);
                helper.assertTrue(mass != null && mass > 0, "sublevel_mass should weigh the lifted block, got " + mass);
                ScriptCommandFailure failure = ZPSScripts.get().tryRun("sublevel_set_name \"zps\"", source(helper), onPlot);
                helper.assertTrue(failure == null, "sublevel_set_name failed: " + failure);
                String name = evaluate(helper, onPlot, "sublevel_name", BuiltInTypes.STRING);
                helper.assertTrue("zps".equals(name), "sublevel_name should read the name it was given, got " + name);
            } finally {
                rig.remove();
            }
            helper.succeed();
        });
    }

    private static <T> T evaluate(GameTestHelper helper, BlockPos target, String expression, ScriptType<T> type) {
        return ZPSScripts.get().evaluate(expression, type, source(helper), target);
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
    }
}
