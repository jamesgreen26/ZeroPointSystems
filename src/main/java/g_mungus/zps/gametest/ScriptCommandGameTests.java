package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.light_pipe.BookHolder;
import g_mungus.zps.commands.api_impl.ZPSCommands;
import g_mungus.zps.commands.api_impl.ZPSScriptCommandSource;
import g_mungus.zps.commands.api_impl.arguments.ValueOfExpression;
import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import g_mungus.zps.util.BookPageTextLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for the ZPS script {@code value_of()} command feature.
 *
 * These tests validate end-to-end evaluation of inner expressions through the
 * dedicated per-type {@link g_mungus.zps.commands.api_impl.ValueOfDispatchers}
 * built during server startup.
 *
 * All tests use a flat 7×4×7 stone platform. The inner expressions use the
 * {@code pos} getter which returns the anchor position set on the
 * {@link ZPSScriptCommandSource}, so results are deterministic for any
 * absolute placement of the template.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScriptCommandGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final ResourceLocation INT_KEY = ResourceLocation.parse("zps:int");
    private static final ResourceLocation DOUBLE_KEY = ResourceLocation.parse("zps:double");
    private static final ResourceLocation BOOLEAN_KEY = ResourceLocation.parse("zps:boolean");
    private static final ResourceLocation STRING_KEY = ResourceLocation.parse("zps:string");

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Creates a fresh {@link ZPSScriptCommandSource} anchored at {@code absPos},
     * then evaluates {@code innerExpression} through the registered value_of
     * dispatcher for {@code typeKey}.
     *
     * @return the {@link ZPSScriptCommandSource#pendingResult} after evaluation
     */
    @SuppressWarnings("unchecked")
    private static <T> T evalValueOf(GameTestHelper helper, BlockPos absPos,
                                     String innerExpression, ResourceLocation typeKey) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();

        ZPSScriptCommandSource innerSource = new ZPSScriptCommandSource(null);
        innerSource.setPos(absPos);

        // withSource swaps the CommandSource so our ZPSScriptCommandSource is visible
        // to the inner dispatcher's getter/mapper lambdas via context.getSource().source
        var innerStack = server.createCommandSourceStack()
                .withSource(innerSource)
                .withLevel(level);

        ValueOfExpression<T> expr = new ValueOfExpression<>(innerExpression, typeKey);
        return expr.evaluate(innerStack, absPos);
    }

    private static int runScriptCommand(GameTestHelper helper, BlockPos absPos, String tail) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String command = ZPSCommands.Paths.SCRIPT + " "
                + absPos.getX() + " " + absPos.getY() + " " + absPos.getZ() + " "
                + tail;
        return server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(level),
                command
        );
    }

    private static void assertStoredRedstone(GameTestHelper helper, BlockPos absPos, int expected, String context) {
        int actual = SetRedstoneCommand.getRedstonePowerAt(helper.getLevel(), absPos);
        if (actual != expected) {
            helper.fail(context + ": expected stored redstone " + expected + ", got " + actual);
        }
    }

    private static BlockPos prepareCommandTarget(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(3, 1, 3);
        helper.setBlock(relPos, Blocks.STONE);
        return helper.absolutePos(relPos);
    }

    private static BlockPos prepareLecternCommandTarget(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(3, 1, 3);
        helper.setBlock(relPos, ModBlocks.DATA_LECTERN.get());

        BlockPos absPos = helper.absolutePos(relPos);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absPos);
        if (!(blockEntity instanceof g_mungus.zps.blockentity.light_pipe.DataLecternBlockEntity lectern)) {
            helper.fail("Expected data lectern block entity at " + absPos + ", got " + blockEntity);
            return absPos;
        }

        lectern.setBook(new ItemStack(Items.WRITABLE_BOOK));
        return absPos;
    }

    private static String getCurrentPageContents(GameTestHelper helper, BlockPos absPos) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absPos);
        if (!(blockEntity instanceof BookHolder holder) || !holder.zps$hasBook()) {
            helper.fail("Expected book holder with a book at " + absPos + ", got " + blockEntity);
            return "";
        }

        ItemStack book = holder.zps$getBook();
        if (book == null) {
            helper.fail("Expected non-null book at " + absPos);
            return "";
        }

        int page = holder.zps$getCurrentPage();
        CompoundTag tag = book.getTag();
        if (tag == null) {
            return "";
        }

        ListTag pages = tag.getList("pages", Tag.TAG_STRING);
        if (page < 0 || page >= pages.size()) {
            return "";
        }
        return pages.getString(page);
    }

    // -------------------------------------------------------------------------
    // Basic coordinate retrieval
    // -------------------------------------------------------------------------

    /**
     * {@code value_of(pos x)} should return the X coordinate of the anchor position.
     *
     * Expression: getter "pos" → BlockPos; mapper "x" → int
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_posX_returnsXCoordinate(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 1, 3));

        Integer result = evalValueOf(helper, absPos, "pos x", INT_KEY);

        if (result == null || result != absPos.getX()) {
            helper.fail("value_of(pos x): expected " + absPos.getX() + ", got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos y)} should return the Y coordinate.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_posY_returnsYCoordinate(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 2, 3));

        Integer result = evalValueOf(helper, absPos, "pos y", INT_KEY);

        if (result == null || result != absPos.getY()) {
            helper.fail("value_of(pos y): expected " + absPos.getY() + ", got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos z)} should return the Z coordinate.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_posZ_returnsZCoordinate(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 1, 5));

        Integer result = evalValueOf(helper, absPos, "pos z", INT_KEY);

        if (result == null || result != absPos.getZ()) {
            helper.fail("value_of(pos z): expected " + absPos.getZ() + ", got " + result);
            return;
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Arithmetic mapper chains inside value_of
    // -------------------------------------------------------------------------

    /**
     * {@code value_of(pos x + 10)} should return X + 10.
     *
     * Expression: getter "pos" → BlockPos; mapper "x" → int; mapper2 "+" 10 → int
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_posXPlusLiteral_returnsSum(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 1, 3));
        int expectedX = absPos.getX();

        Integer result = evalValueOf(helper, absPos, "pos x + 10", INT_KEY);

        if (result == null || result != expectedX + 10) {
            helper.fail("value_of(pos x + 10): expected " + (expectedX + 10) + ", got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos x - 1)} should return X - 1.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_posXMinusLiteral_returnsDifference(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 1, 3));
        int expectedX = absPos.getX();

        Integer result = evalValueOf(helper, absPos, "pos x - 1", INT_KEY);

        if (result == null || result != expectedX - 1) {
            helper.fail("value_of(pos x - 1): expected " + (expectedX - 1) + ", got " + result);
            return;
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Nested value_of
    // -------------------------------------------------------------------------

    /**
     * {@code value_of(pos x + value_of(pos y))} should return X + Y.
     *
     * The inner {@code value_of(pos y)} is itself evaluated recursively via a
     * fresh {@link ZPSScriptCommandSource}.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_nested_returnsXPlusY(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 2, 3));
        int expectedX = absPos.getX();
        int expectedY = absPos.getY();

        Integer result = evalValueOf(helper, absPos, "pos x + value_of(pos y)", INT_KEY);

        int expected = expectedX + expectedY;
        if (result == null || result != expected) {
            helper.fail("value_of(pos x + value_of(pos y)): expected " + expected
                    + " (X=" + expectedX + " Y=" + expectedY + "), got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos x + value_of(pos x - value_of(pos y)))} — two levels of
     * nesting — should return X + (X - Y).
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_deeplyNested_correctResult(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 2, 3));
        int x = absPos.getX();
        int y = absPos.getY();

        Integer result = evalValueOf(helper, absPos,
                "pos x + value_of(pos x - value_of(pos y))", INT_KEY);

        int expected = x + (x - y);
        if (result == null || result != expected) {
            helper.fail("value_of deeply nested: expected " + expected
                    + " (X=" + x + " Y=" + y + "), got " + result);
            return;
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Double dispatcher
    // -------------------------------------------------------------------------

    /**
     * {@code value_of(pos x * 2.5)} should return X * 2.5 as a double via the
     * zps:double inner dispatcher (rounded_down back to int via outer context).
     *
     * This specifically tests that the zps:double inner dispatcher is built and
     * that {@code *} (int × double → double) works correctly.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_posXTimesDouble_returnsDouble(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(4, 1, 3));
        double expectedX = absPos.getX();

        Double result = evalValueOf(helper, absPos, "pos x * 2.5", DOUBLE_KEY);

        double expected = expectedX * 2.5;
        if (result == null || Math.abs(result - expected) > 1e-9) {
            helper.fail("value_of(pos x * 2.5): expected " + expected + ", got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos as_string <+ "Pos: ")} should prepend the literal to
     * the computed string form of the position.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_stringPrepend_returnsPrependedString(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(4, 1, 3));

        String result = evalValueOf(helper, absPos, "pos as_string <+ \"Pos: \"", STRING_KEY);
        String expected = "Pos: " + absPos.getX() + " " + absPos.getY() + " " + absPos.getZ();

        if (!expected.equals(result)) {
            helper.fail("value_of(pos as_string <+ \"Pos: \"): expected " + expected + ", got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos as_string + "\\nalpha\\n" lines)} should count escaped
     * newline separators and preserve the trailing empty line.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_stringLines_countsEscapedNewlines(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(4, 1, 3));

        Integer result = evalValueOf(helper, absPos, "pos as_string + \"\\\\nalpha\\\\n\" lines", INT_KEY);

        if (result == null || result != 3) {
            helper.fail("value_of(pos as_string + \"\\\\nalpha\\\\n\" lines): expected 3, got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos as_string + "\\nalpha\\nbeta" get_line 3)} should
     * return the requested escaped-newline-delimited segment using 1-based
     * indexing.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_stringGetLine_returnsIndexedLine(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(4, 1, 3));

        String result = evalValueOf(helper, absPos, "pos as_string + \"\\\\nalpha\\\\nbeta\" get_line 3", STRING_KEY);

        if (!"beta".equals(result)) {
            helper.fail("value_of(pos as_string + \"\\\\nalpha\\\\nbeta\" get_line 3): expected beta, got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos as_string + "\\nalpha" get_line 5)} should return an
     * empty string when the requested index is out of bounds.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_stringGetLine_outOfBoundsReturnsEmptyString(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(4, 1, 3));

        String result = evalValueOf(helper, absPos, "pos as_string + \"\\\\nalpha\" get_line 5", STRING_KEY);

        if (!"".equals(result)) {
            helper.fail("value_of(pos as_string + \"\\\\nalpha\" get_line 5): expected empty string, got " + result);
            return;
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Bitwise operators (regression for & and |)
    // -------------------------------------------------------------------------

    /**
     * {@code value_of(pos x & 15)} — bitwise AND — tests that {@code &} is
     * available inside a value_of inner expression.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_bitwiseAnd_returnsCorrectBits(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(3, 1, 3));
        int x = absPos.getX();

        Integer result = evalValueOf(helper, absPos, "pos x & 15", INT_KEY);

        int expected = x & 15;
        if (result == null || result != expected) {
            helper.fail("value_of(pos x & 15): expected " + expected + ", got " + result);
            return;
        }
        helper.succeed();
    }

    /**
     * {@code value_of(pos x | 1)} — bitwise OR — ensures the LSB is always set.
     */
    @GameTest(template = TEMPLATE)
    public static void valueOf_bitwiseOr_setsLSB(GameTestHelper helper) {
        BlockPos absPos = helper.absolutePos(new BlockPos(4, 1, 3));
        int x = absPos.getX();

        Integer result = evalValueOf(helper, absPos, "pos x | 1", INT_KEY);

        int expected = x | 1;
        if (result == null || result != expected) {
            helper.fail("value_of(pos x | 1): expected " + expected + ", got " + result);
            return;
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // End-to-end command execution
    // -------------------------------------------------------------------------

    /**
     * The public {@code /zps_script} command should still accept literal executor
     * arguments when run through the real command dispatcher.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_literalExecutorArgument_setsRedstone(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);

        int result = runScriptCommand(helper, absPos, "set_redstone 7");
        if (result != 1) {
            helper.fail("zps_script literal set_redstone returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 7, "zps_script literal set_redstone");
        helper.succeed();
    }

    /**
     * Executor arguments should accept {@code value_of(...)} in the same way they
     * do through the internal helper path.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_executorValueOfArgument_setsComputedRedstone(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);
        int expected = absPos.getX() & 15;

        int result = runScriptCommand(helper, absPos, "set_redstone value_of(pos x & 15)");
        if (result != 1) {
            helper.fail("zps_script executor value_of returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, expected, "zps_script executor value_of");
        helper.succeed();
    }

    /**
     * Conditional public commands should support {@code value_of(...)} inside the
     * boolean branch and then execute the correct executor branch.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_ifElseWithValueOf_usesCorrectBranch(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);

        int result = runScriptCommand(
                helper,
                absPos,
                "if pos x < value_of(pos y) set_redstone 3 else set_redstone 12"
        );
        if (result != 1) {
            helper.fail("zps_script if/else returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 12, "zps_script if/else");
        helper.succeed();
    }

    /**
     * First condition false, second condition true: should execute the
     * {@code else if} branch.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_ifElseIf_firstFalseSecondTrue_executesElseIf(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);
        BlockPos falsePos = absPos.offset(1, 0, 0);

        int result = runScriptCommand(
                helper,
                absPos,
                "if pos == " + falsePos.getX() + " " + falsePos.getY() + " " + falsePos.getZ()
                        + " set_redstone 1 else if pos == " + absPos.getX() + " " + absPos.getY() + " " + absPos.getZ()
                        + " set_redstone 2 else set_redstone 3"
        );
        if (result != 1) {
            helper.fail("zps_script if/else if returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 2, "zps_script if/else if");
        helper.succeed();
    }

    /**
     * First condition true, second condition false: should execute the first
     * {@code if} branch.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_ifElseIf_firstTrueSecondFalse_executesIf(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);
        BlockPos falsePos = absPos.offset(1, 0, 0);

        int result = runScriptCommand(
                helper,
                absPos,
                "if pos == " + absPos.getX() + " " + absPos.getY() + " " + absPos.getZ()
                        + " set_redstone 1 else if pos == " + falsePos.getX() + " " + falsePos.getY() + " " + falsePos.getZ()
                        + " set_redstone 2 else set_redstone 3"
        );
        if (result != 1) {
            helper.fail("zps_script if/else if first-true second-false returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 1, "zps_script if/else if first-true second-false");
        helper.succeed();
    }

    /**
     * Both conditions true: should execute the first {@code if} branch.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_ifElseIf_bothTrue_executesFirstIf(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);

        int result = runScriptCommand(
                helper,
                absPos,
                "if pos == " + absPos.getX() + " " + absPos.getY() + " " + absPos.getZ()
                        + " set_redstone 1 else if pos == " + absPos.getX() + " " + absPos.getY() + " " + absPos.getZ()
                        + " set_redstone 2 else set_redstone 3"
        );
        if (result != 1) {
            helper.fail("zps_script if/else if both-true returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 1, "zps_script if/else if both-true");
        helper.succeed();
    }

    /**
     * Both conditions false: should execute the {@code else} branch.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_ifElseIf_bothFalse_executesElse(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);
        BlockPos falsePosA = absPos.offset(1, 0, 0);
        BlockPos falsePosB = absPos.offset(2, 0, 0);

        int result = runScriptCommand(
                helper,
                absPos,
                "if pos == " + falsePosA.getX() + " " + falsePosA.getY() + " " + falsePosA.getZ()
                        + " set_redstone 1 else if pos == " + falsePosB.getX() + " " + falsePosB.getY() + " " + falsePosB.getZ()
                        + " set_redstone 2 else set_redstone 3"
        );
        if (result != 1) {
            helper.fail("zps_script if/else if both-false returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 3, "zps_script if/else if both-false");
        helper.succeed();
    }

    /**
     * Nested {@code value_of(...)} should work through the public command path,
     * not just the inner dispatcher helper.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_nestedValueOfInExecutor_setsComputedRedstone(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);
        int expected = absPos.getX() + (absPos.getX() - absPos.getY());

        int result = runScriptCommand(
                helper,
                absPos,
                "set_redstone value_of(pos x + value_of(pos x - value_of(pos y)))"
        );
        if (result != 1) {
            helper.fail("zps_script nested value_of returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, expected, "zps_script nested value_of");
        helper.succeed();
    }

    /**
     * Multiple separate {@code value_of(...)} calls in one command should each
     * evaluate independently through the real {@code /zps_script} path.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_multipleValueOfInSingleCommand_usesBothResults(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);
        int expected = absPos.getZ() | 1;

        int result = runScriptCommand(
                helper,
                absPos,
                "if pos x < value_of(pos y + 200) set_redstone value_of(pos z | 1) else set_redstone 0"
        );
        if (result != 1) {
            helper.fail("zps_script multiple value_of returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, expected, "zps_script multiple value_of");
        helper.succeed();
    }

    /**
     * {@code direction_to value_of(...)} should accept an evaluated Vec3
     * argument, not just literal coordinates.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_directionToWithValueOfVecPosArgument_setsComputedRedstone(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);

        int result = runScriptCommand(
                helper,
                absPos,
                "set_redstone value_of(pos center direction_to value_of(pos center) length * 15 rounded_down)"
        );
        if (result != 1) {
            helper.fail("zps_script direction_to value_of returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 0, "zps_script direction_to value_of");
        helper.succeed();
    }

    /**
     * {@code dot value_of(...)} should accept an evaluated Vec3 direction
     * argument, not just literal coordinates.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_dotWithValueOfVecDirArgument_setsComputedRedstone(GameTestHelper helper) {
        BlockPos absPos = prepareCommandTarget(helper);
        double targetX = absPos.getX() + 10.5;
        double targetY = absPos.getY() + 0.5;
        double targetZ = absPos.getZ() + 0.5;

        int result = runScriptCommand(
                helper,
                absPos,
                "set_redstone value_of(pos center direction_to "
                        + targetX + " " + targetY + " " + targetZ
                        + " dot value_of(pos center direction_to "
                        + targetX + " " + targetY + " " + targetZ
                        + ") * 15 rounded_down)"
        );
        if (result != 1) {
            helper.fail("zps_script dot value_of returned " + result + " instead of 1");
            return;
        }

        assertStoredRedstone(helper, absPos, 15, "zps_script dot value_of");
        helper.succeed();
    }

    /**
     * {@code write_page value_of(dimension as_string + "(")} should evaluate the
     * string expression through the public command path and write the result to
     * the current data lectern page.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_writePageWithValueOfDimensionString_writesComputedPage(GameTestHelper helper) {
        BlockPos absPos = prepareLecternCommandTarget(helper);
        String expected = helper.getLevel().dimension().location() + "(";

        int result = runScriptCommand(
                helper,
                absPos,
                "write_page value_of(dimension as_string + \"(\")"
        );
        if (result != 1) {
            helper.fail("zps_script write_page value_of returned " + result + " instead of 1");
            return;
        }

        helper.succeedWhen(() -> {
            String actual = getCurrentPageContents(helper, absPos);
            if (!expected.equals(actual)) {
                helper.fail("zps_script write_page value_of: expected page \"" + expected + "\", got \"" + actual + "\"");
            }
        });
    }

    /**
     * {@code write_page} should enforce the same visible-page limit used by the
     * data transcriber when the input text is longer than a writable page can
     * display.
     */
    @GameTest(template = TEMPLATE)
    public static void zpsScript_writePage_truncatesOverlongTextToBookDisplayLimit(GameTestHelper helper) {
        BlockPos absPos = prepareLecternCommandTarget(helper);
        String longText = "a".repeat(600);
        String expected = BookPageTextLimiter.truncateToDisplayableLength(longText);

        int result = runScriptCommand(
                helper,
                absPos,
                "write_page " + longText
        );
        if (result != 1) {
            helper.fail("zps_script write_page overlong returned " + result + " instead of 1");
            return;
        }

        helper.succeedWhen(() -> {
            String actual = getCurrentPageContents(helper, absPos);
            if (!expected.equals(actual)) {
                helper.fail("zps_script write_page overlong: expected length "
                        + expected.length() + ", got length " + actual.length());
            }
        });
    }
}
