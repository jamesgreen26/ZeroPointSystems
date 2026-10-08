package g_mungus.zps.gametest;

import g_mungus.munguscript.language.builtin.BuiltInTypes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.commands.api_impl.ZPSScripts;
import g_mungus.zps.compat.Compat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Create's scroll settings from scripts: the {@code set_*} executors made for them, and the getters
 * that read them back. Create is only on the classpath for local runs; without it these pass
 * without doing anything.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreateScriptGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos TARGET = new BlockPos(3, 1, 3);

    /** A creative motor's speed, a number, is set and read back under the name its mapping gives it. */
    @GameTest(template = TEMPLATE)
    public static void numberSettingReadsBackWhatWasSet(GameTestHelper helper) {
        if (!Compat.isCreateLoaded()) {
            helper.succeed();
            return;
        }
        BlockPos target = place(helper, "creative_motor");
        run(helper, target, "set_rpm 64");
        Integer rpm = evaluate(helper, target, "rpm", BuiltInTypes.INT);
        helper.assertTrue(rpm == 64, "rpm should read the 64 it was set to, got " + rpm);
        helper.succeed();
    }

    /** A mechanical bearing's rotation mode, an option, reads back as the word that set it. */
    @GameTest(template = TEMPLATE)
    public static void optionSettingReadsBackAsItsName(GameTestHelper helper) {
        if (!Compat.isCreateLoaded()) {
            helper.succeed();
            return;
        }
        BlockPos target = place(helper, "mechanical_bearing");
        run(helper, target, "set_rotation_mode ROTATE_NEVER_PLACE");
        String mode = evaluate(helper, target, "rotation_mode", BuiltInTypes.STRING);
        helper.assertTrue("ROTATE_NEVER_PLACE".equals(mode),
                "rotation_mode should read the option it was set to, got " + mode);
        helper.succeed();
    }

    /** Pointed at a block without the setting, a getter reads its empty value rather than failing. */
    @GameTest(template = TEMPLATE)
    public static void gettersReadEmptyElsewhere(GameTestHelper helper) {
        if (!Compat.isCreateLoaded()) {
            helper.succeed();
            return;
        }
        helper.setBlock(TARGET, BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("stone")));
        BlockPos target = helper.absolutePos(TARGET);
        helper.assertTrue(evaluate(helper, target, "rpm", BuiltInTypes.INT) == 0, "rpm should read 0 on stone");
        helper.assertTrue(evaluate(helper, target, "rotation_mode", BuiltInTypes.STRING).isEmpty(),
                "rotation_mode should read empty on stone");
        helper.succeed();
    }

    private static BlockPos place(GameTestHelper helper, String createBlock) {
        helper.setBlock(TARGET, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", createBlock)));
        return helper.absolutePos(TARGET);
    }

    private static void run(GameTestHelper helper, BlockPos target, String command) {
        ScriptCommandFailure failure = ZPSScripts.get().tryRun(command, source(helper), target);
        helper.assertTrue(failure == null, "'" + command + "' failed: " + failure);
    }

    private static <T> T evaluate(GameTestHelper helper, BlockPos target, String expression, ScriptType<T> type) {
        return ZPSScripts.get().evaluate(expression, type, source(helper), target);
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
    }
}
