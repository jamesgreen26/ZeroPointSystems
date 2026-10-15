package g_mungus.zps.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.DataCombinator;
import g_mungus.zps.block.cableNetwork.light_pipe.DataComparator;
import g_mungus.zps.block.cableNetwork.light_pipe.DataLecternBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.DataTranscriberBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusMode;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.blockentity.RedstoneConverterBlockEntity;
import g_mungus.zps.blockentity.light_pipe.DataLecternBlockEntity;
import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import g_mungus.zps.commands.content.TerminalLines;
import g_mungus.zps.commands.api_impl.ScriptCommandFailure;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import g_mungus.zps.compat.create.CreateCompat;
import g_mungus.zps.compat.create.DisplayLinkManualTextAccessor;
import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import g_mungus.zps.util.BookComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightPipeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private LightPipeGameTests() {
    }

    private static BlockState lightPipe() {
        return ModBlocks.DATA_CABLE.get().defaultBlockState();
    }

    private static BlockState lectern(Direction facing) {
        return ModBlocks.DATA_LECTERN.get().defaultBlockState()
                .setValue(DataLecternBlock.FACING, facing)
                .setValue(DataLecternBlock.HAS_BOOK, true);
    }

    private static BlockState display(Direction facing) {
        return ModBlocks.TEXT_DISPLAY.get().defaultBlockState()
                .setValue(TextDisplayBlock.FACING, facing);
    }

    private static BlockState combinator(Direction facing, DataCombinator.CombineMode mode) {
        return ModBlocks.DATA_COMBINATOR.get().defaultBlockState()
                .setValue(DataCombinator.FACING, facing)
                .setValue(DataCombinator.MODE, mode);
    }

    private static BlockState comparator(Direction facing, DataComparator.ComparisonMode mode) {
        return ModBlocks.DATA_COMPARATOR.get().defaultBlockState()
                .setValue(DataComparator.FACING, facing)
                .setValue(DataComparator.MODE, mode);
    }

    private static BlockState transcriber(Direction facing) {
        return ModBlocks.DATA_TRANSCRIBER.get().defaultBlockState()
                .setValue(DataTranscriberBlock.FACING, facing);
    }

    private static BlockState serialBus(Direction facing) {
        return ModBlocks.SERIAL_BUS.get().defaultBlockState()
                .setValue(SerialBusBlock.FACING, facing);
    }

    private static BlockState displayLink(Direction facing) {
        return AllBlocks.DISPLAY_LINK.getDefaultState()
                .setValue(DirectionalBlock.FACING, facing);
    }

    private static DataLecternBlockEntity lecternEntity(GameTestHelper helper, BlockPos relPos) {
        BlockEntity blockEntity = helper.getBlockEntity(relPos);
        if (!(blockEntity instanceof DataLecternBlockEntity lectern)) {
            helper.fail("Expected data lectern block entity at " + relPos + ", got " + blockEntity);
            return null;
        }
        return lectern;
    }

    private static TextDisplayBlockEntity textDisplayEntity(GameTestHelper helper, BlockPos relPos) {
        BlockEntity blockEntity = helper.getBlockEntity(relPos);
        if (!(blockEntity instanceof TextDisplayBlockEntity display)) {
            helper.fail("Expected text display block entity at " + relPos + ", got " + blockEntity);
            return null;
        }
        return display;
    }

    /** The power a bus is emitting into the block it faces, or -1 without a bus. */
    private static int busOutput(GameTestHelper helper, BlockPos serialBusPos) {
        return helper.getBlockEntity(serialBusPos) instanceof SerialBusBlockEntity bus ? bus.getRedstoneOutput() : -1;
    }

    private static SerialBusBlockEntity serialBusEntity(GameTestHelper helper, BlockPos relPos) {
        BlockEntity blockEntity = helper.getBlockEntity(relPos);
        if (!(blockEntity instanceof SerialBusBlockEntity serialBus)) {
            helper.fail("Expected serial bus block entity at " + relPos + ", got " + blockEntity);
            return null;
        }
        return serialBus;
    }

    private static DisplayLinkBlockEntity displayLinkEntity(GameTestHelper helper, BlockPos relPos) {
        BlockEntity blockEntity = helper.getBlockEntity(relPos);
        if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink)) {
            helper.fail("Expected display link block entity at " + relPos + ", got " + blockEntity);
            return null;
        }
        return displayLink;
    }

    private static void setWritableBook(GameTestHelper helper, BlockPos relPos, String... pages) {
        DataLecternBlockEntity lectern = lecternEntity(helper, relPos);
        if (lectern == null) {
            return;
        }

        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        ListTag pageList = new ListTag();
        for (String page : pages) {
            pageList.add(StringTag.valueOf(page));
        }
        book.getOrCreateTag().put("pages", pageList);
        lectern.setBook(book);
    }

    private static String displayText(GameTestHelper helper, BlockPos relPos) {
        TextDisplayBlockEntity display = textDisplayEntity(helper, relPos);
        return display == null ? "" : display.getDisplayText();
    }

    private static String currentPageText(GameTestHelper helper, BlockPos relPos) {
        DataLecternBlockEntity lectern = lecternEntity(helper, relPos);
        if (lectern == null) {
            return "";
        }
        ItemStack book = lectern.getBook();
        CompoundTag tag = book.getTag();
        if (tag == null || !tag.contains("pages", Tag.TAG_LIST)) {
            return "";
        }
        ListTag pages = tag.getList("pages", Tag.TAG_STRING);
        int page = lectern.getPage();
        if (page < 0 || page >= pages.size()) {
            return "";
        }
        String text = pages.getString(page);
        if (book.is(Items.WRITTEN_BOOK)) {
            Component component = Component.Serializer.fromJson(text);
            return component == null ? "" : component.getString();
        }
        return text;
    }

    // --- script variables ------------------------------------------------------------------------

    private static final BlockPos VAR_BUS_A = new BlockPos(2, 1, 3);
    private static final BlockPos VAR_BUS_B = new BlockPos(4, 1, 3);
    private static final BlockPos VAR_TERMINAL = new BlockPos(3, 1, 5);

    /**
     * Two buses facing north onto stone at x 2 and x 4, a pipe behind them, and a Script Terminal
     * running {@code script} on the shortest delay, powered once the script is in.
     */
    private static ScriptTerminalBlockEntity variableRig(GameTestHelper helper, String script) {
        return variableRig(helper, script, 2, false);
    }

    /** As {@link #variableRig(GameTestHelper, String)}, on {@code delay} ticks a command, looping if asked. */
    private static ScriptTerminalBlockEntity variableRig(GameTestHelper helper, String script, int delay, boolean loop) {
        for (BlockPos bus : List.of(VAR_BUS_A, VAR_BUS_B)) {
            helper.setBlock(bus.north(), Blocks.STONE.defaultBlockState());
            helper.setBlock(bus, serialBus(Direction.NORTH));
        }
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), lightPipe());
        }
        helper.setBlock(VAR_TERMINAL, ModBlocks.SCRIPT_TERMINAL.get().defaultBlockState()
                .setValue(ScriptTerminalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(VAR_TERMINAL) instanceof ScriptTerminalBlockEntity terminal)) {
            helper.fail("Expected a script terminal at " + VAR_TERMINAL);
            return null;
        }
        terminal.acceptUpdatePacket(new ScriptComputerC2SPacket(helper.absolutePos(VAR_TERMINAL), loop, delay, script));
        helper.setBlock(VAR_TERMINAL.south(), Blocks.REDSTONE_BLOCK);
        return terminal;
    }

    /** {@code pos == x y z} for the block a bus faces. */
    private static String target(GameTestHelper helper, BlockPos bus) {
        BlockPos abs = helper.absolutePos(bus.north());
        return "pos == " + abs.getX() + " " + abs.getY() + " " + abs.getZ();
    }

    /** One bus answers, the terminal keeps the value, and a later line hands it to another bus. */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_valueTravelsFromOneBusToAnother(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "if " + target(helper, VAR_BUS_A) + " return 7",
                "receive lvl",
                "if " + target(helper, VAR_BUS_B) + " set_redstone value_of(lvl)"));
        if (terminal == null) {
            return;
        }
        helper.succeedWhen(() -> {
            int output = busOutput(helper, VAR_BUS_B);
            if (output != 7) {
                helper.fail("Expected bus B to emit the received 7, got " + output
                        + " (receive: " + terminal.getLastReceiveFailure() + ")");
            }
            if (busOutput(helper, VAR_BUS_A) != 0) {
                helper.fail("Expected bus A to be left alone, got " + busOutput(helper, VAR_BUS_A));
            }
        });
    }

    /** An answer is a value: another bus hearing it must not run it as a command. */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_answerIsNotRunByOtherBuses(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var text = x",
                "if " + target(helper, VAR_BUS_A) + " return \"set_redstone 9\"",
                "receive text"));
        if (terminal == null) {
            return;
        }
        helper.runAfterDelay(10, () -> {
            if (!"set_redstone 9".equals(terminal.getVariableValue("text"))) {
                helper.fail("Expected the terminal to receive the text, got " + terminal.getVariableValue("text")
                        + " (receive: " + terminal.getLastReceiveFailure() + ")");
                return;
            }
            if (busOutput(helper, VAR_BUS_B) != 0 || busOutput(helper, VAR_BUS_A) != 0) {
                helper.fail("Expected no bus to run the answer, got outputs " + busOutput(helper, VAR_BUS_A)
                        + " and " + busOutput(helper, VAR_BUS_B));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * An answer is on the cable for one tick, like a terminal's command: the bus has taken it off
     * again while a Text Display still holds it, and the terminal has kept it for {@code receive}.
     */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_answerIsOnTheCableForOneTick(GameTestHelper helper) {
        BlockPos displayPos = new BlockPos(1, 1, 4);
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "if " + target(helper, VAR_BUS_A) + " return 5",
                "wait 4",
                "receive lvl"));
        if (terminal == null) {
            return;
        }
        helper.setBlock(displayPos, display(Direction.WEST));
        helper.runAfterDelay(5, () -> {
            SerialBusBlockEntity busA = serialBusEntity(helper, VAR_BUS_A);
            if (busA == null) {
                return;
            }
            String sending = busA.provideNextDisplayText(100);
            String shown = displayText(helper, displayPos);
            if (!sending.isEmpty() || !"provide_value 5".equals(shown)) {
                helper.fail("Expected the answer off the cable but still on the display, the bus sends \""
                        + sending + "\" and the display shows \"" + shown + "\"");
            }
        });
        helper.succeedWhen(() -> {
            if (!"5".equals(terminal.getVariableValue("lvl"))) {
                helper.fail("Expected the terminal to keep the answer for receive, got " + terminal.getVariableValue("lvl")
                        + " (receive: " + terminal.getLastReceiveFailure() + ")");
            }
        });
    }

    /** An answer belongs to the line before it: a command sent in between loses it for a later {@code receive}. */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_commandBetweenReturnAndReceiveLosesTheAnswer(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "if " + target(helper, VAR_BUS_A) + " return 5",
                "if " + target(helper, VAR_BUS_B) + " set_redstone 3",
                "receive lvl"));
        if (terminal == null) {
            return;
        }
        helper.runAfterDelay(12, () -> {
            String failure = terminal.getLastReceiveFailure();
            if (busOutput(helper, VAR_BUS_B) != 3) {
                helper.fail("Expected the command in between to run, bus B emits " + busOutput(helper, VAR_BUS_B));
                return;
            }
            if (terminal.getVariableValue("lvl") != null || failure == null || !failure.startsWith("Nothing was received")) {
                helper.fail("Expected the answer to be lost to the command in between, got value "
                        + terminal.getVariableValue("lvl") + " and failure " + failure);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The command after an answer reaches the cable clean, never garbled with the answer, on the
     * shortest delay where the answer comes off in the very tick the command goes on.
     */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_commandAfterAnAnswerArrivesClean(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "if " + target(helper, VAR_BUS_A) + " return 5",
                "if " + target(helper, VAR_BUS_B) + " set_redstone 7",
                "if " + target(helper, VAR_BUS_A) + " return 6",
                "if " + target(helper, VAR_BUS_B) + " set_redstone 9"));
        if (terminal == null) {
            return;
        }
        helper.runAfterDelay(12, () -> {
            SerialBusBlockEntity busB = serialBusEntity(helper, VAR_BUS_B);
            if (busB == null) {
                return;
            }
            String expected = "if " + target(helper, VAR_BUS_B) + " set_redstone 9";
            if (busB.getLastOutcome() != SerialBusBlockEntity.Outcome.SUCCESS || !expected.equals(busB.getLastCommand())
                    || busOutput(helper, VAR_BUS_B) != 9) {
                helper.fail("Expected bus B to run each command clean, its last was \"" + busB.getLastCommand()
                        + "\" (" + busB.getLastOutcome() + ") emitting " + busOutput(helper, VAR_BUS_B));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * {@code receive} takes no time: on a 4 tick delay the line after it runs in the same tick, 4
     * ticks after the {@code return}, where a cycle for the receive would put it 8 ticks after.
     */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_receiveTakesNoTime(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "if " + target(helper, VAR_BUS_A) + " return 5",
                "receive lvl",
                "if " + target(helper, VAR_BUS_B) + " set_redstone value_of(lvl)"), 4, false);
        if (terminal == null) {
            return;
        }
        helper.runAfterDelay(6, () -> {
            if (busOutput(helper, VAR_BUS_B) != 5) {
                helper.fail("Expected the line after receive to have run by now, bus B emits "
                        + busOutput(helper, VAR_BUS_B) + " (receive: " + terminal.getLastReceiveFailure() + ")");
                return;
            }
            helper.succeed();
        });
    }

    /** A looping script of nothing but free lines still takes time per pass, rather than spinning in a tick. */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_loopOfFreeLinesDoesNotSpin(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "receive lvl",
                "receive lvl"), 2, true);
        if (terminal == null) {
            return;
        }
        helper.runAfterDelay(10, () -> {
            String failure = terminal.getLastReceiveFailure();
            if (failure == null || !failure.startsWith("Nothing was received")) {
                helper.fail("Expected the looping receives to run, got failure " + failure);
                return;
            }
            helper.succeed();
        });
    }

    /** Two buses answering at once garble the cable, so nothing is received and the variable is left alone. */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_twoAnswersLeaveTheVariableAlone(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "return 3",
                "receive lvl"));
        if (terminal == null) {
            return;
        }
        helper.runAfterDelay(10, () -> {
            String failure = terminal.getLastReceiveFailure();
            if (terminal.getVariableValue("lvl") != null || failure == null || !failure.startsWith("Nothing was received")) {
                helper.fail("Expected two answers to garble the cable, got value " + terminal.getVariableValue("lvl")
                        + " and failure " + failure);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Any value is received, whatever the variable was declared as; one that does not suit where
     * it is used fails there, on the bus that runs the line.
     */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_anyValueIsReceivedAndAMisfitFailsWhereUsed(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "if " + target(helper, VAR_BUS_A) + " return hello",
                "receive lvl",
                "if " + target(helper, VAR_BUS_B) + " set_redstone value_of(lvl)"));
        if (terminal == null) {
            return;
        }
        helper.runAfterDelay(12, () -> {
            if (!"hello".equals(terminal.getVariableValue("lvl")) || terminal.getLastReceiveFailure() != null) {
                helper.fail("Expected 'hello' to be received, got value " + terminal.getVariableValue("lvl")
                        + " and failure " + terminal.getLastReceiveFailure());
                return;
            }
            SerialBusBlockEntity busB = serialBusEntity(helper, VAR_BUS_B);
            if (busB == null) {
                return;
            }
            if (busB.getLastOutcome() != SerialBusBlockEntity.Outcome.FAILURE || busOutput(helper, VAR_BUS_B) != 0) {
                helper.fail("Expected bus B to fail on set_redstone 'hello', got " + busB.getLastOutcome()
                        + " with output " + busOutput(helper, VAR_BUS_B));
                return;
            }
            helper.succeed();
        });
    }

    /** {@code wait} sends nothing, so the answer to the line before it is still there to receive. */
    @GameTest(template = TEMPLATE)
    public static void scriptVariable_waitBetweenReturnAndReceiveKeepsTheAnswer(GameTestHelper helper) {
        ScriptTerminalBlockEntity terminal = variableRig(helper, String.join("\n",
                "#var lvl = 0",
                "if " + target(helper, VAR_BUS_A) + " return 5",
                "wait   2",
                "receive \t lvl"));
        if (terminal == null) {
            return;
        }
        helper.succeedWhen(() -> {
            if (!"5".equals(terminal.getVariableValue("lvl"))) {
                helper.fail("Expected 5 after the wait, got " + terminal.getVariableValue("lvl")
                        + " (receive: " + terminal.getLastReceiveFailure() + ")");
            }
        });
    }

    /** {@code wait} and {@code receive} take any whitespace around their argument, and only as a separate word. */
    @GameTest(template = TEMPLATE)
    public static void terminalLines_toleratesWhitespace(GameTestHelper helper) {
        List<String> problems = new java.util.ArrayList<>();
        check(problems, "wait  2 cycles", TerminalLines.waitCycles("wait  2"), 2);
        check(problems, "wait tab cycles", TerminalLines.waitCycles("wait\t3 "), 3);
        check(problems, "bare wait is a wait", TerminalLines.isWait("wait"), true);
        check(problems, "bare wait has no cycles", TerminalLines.waitCycles("wait"), null);
        check(problems, "wait 64 is in range", TerminalLines.waitCycles("wait 64"), 64);
        check(problems, "wait 65 is out of range", TerminalLines.waitCycles("wait 65"), null);
        check(problems, "wait 0 is out of range", TerminalLines.waitCycles("wait 0"), null);
        check(problems, "waiting is not a wait", TerminalLines.isWait("waiting 2"), false);
        check(problems, "receive name", TerminalLines.argument("receive   lvl ", TerminalLines.RECEIVE), "lvl");
        check(problems, "receiver is not a receive", TerminalLines.isReceive("receiver lvl"), false);
        check(problems, "answer value", TerminalLines.providedValue("provide_value  a b "), " a b ");
        check(problems, "empty answer", TerminalLines.providedValue("provide_value "), "");
        check(problems, "bare provide_value is no answer", TerminalLines.providedValue("provide_value"), null);
        check(problems, "plain text is no answer", TerminalLines.providedValue("5"), null);
        if (!problems.isEmpty()) {
            helper.fail(String.join("; ", problems));
            return;
        }
        helper.succeed();
    }

    private static void check(List<String> problems, String what, Object actual, Object expected) {
        if (!java.util.Objects.equals(actual, expected)) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }

    /** A clear is held back {@link TextDisplayBlockEntity#CLEAR_HOLD_TICKS} ticks, then lands. */
    @GameTest(template = TEMPLATE)
    public static void textDisplay_holdsTextBrieflyAfterClear(GameTestHelper helper) {
        BlockPos displayPos = new BlockPos(3, 1, 3);
        helper.setBlock(displayPos, display(Direction.WEST));
        TextDisplayBlockEntity display = textDisplayEntity(helper, displayPos);
        if (display == null) {
            return;
        }
        display.acceptText(0, "held");
        display.acceptText(0, "");

        int hold = TextDisplayBlockEntity.CLEAR_HOLD_TICKS;
        helper.runAfterDelay(hold - 1, () -> {
            if (!"held".equals(display.getDisplayText())) {
                helper.fail("Expected the text to be held just before the clear lands, got \"" + display.getDisplayText() + "\"");
            }
        });
        helper.runAfterDelay(hold + 1, () -> {
            if (!display.getDisplayText().isEmpty()) {
                helper.fail("Expected the text to be cleared after the hold, got \"" + display.getDisplayText() + "\"");
                return;
            }
            helper.succeed();
        });
    }

    /** New text replaces the old at once, and cancels a clear that was pending. */
    @GameTest(template = TEMPLATE)
    public static void textDisplay_newTextShowsAtOnceAndCancelsPendingClear(GameTestHelper helper) {
        BlockPos displayPos = new BlockPos(3, 1, 3);
        helper.setBlock(displayPos, display(Direction.WEST));
        TextDisplayBlockEntity display = textDisplayEntity(helper, displayPos);
        if (display == null) {
            return;
        }
        display.acceptText(0, "first");
        display.acceptText(0, "second");
        if (!"second".equals(display.getDisplayText())) {
            helper.fail("Expected new text to show at once, got \"" + display.getDisplayText() + "\"");
            return;
        }
        display.acceptText(0, "");
        display.acceptText(0, "third");
        if (!"third".equals(display.getDisplayText())) {
            helper.fail("Expected text after a clear to show at once, got \"" + display.getDisplayText() + "\"");
            return;
        }

        helper.runAfterDelay(TextDisplayBlockEntity.CLEAR_HOLD_TICKS + 2, () -> {
            if (!"third".equals(display.getDisplayText())) {
                helper.fail("Expected the cancelled clear not to land, got \"" + display.getDisplayText() + "\"");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void dataLectern_turnPageUpdatesConnectedDisplay(GameTestHelper helper) {
        BlockPos displayPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(4, 1, 3);
        BlockPos lecternPos = new BlockPos(5, 1, 3);

        helper.setBlock(displayPos, display(Direction.WEST));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(lecternPos, lectern(Direction.EAST));
        setWritableBook(helper, lecternPos, "alpha", "beta");

        if (!"alpha".equals(displayText(helper, displayPos))) {
            helper.fail("Expected initial lectern page to be displayed");
            return;
        }

        DataLecternBlockEntity lectern = lecternEntity(helper, lecternPos);
        if (lectern == null) {
            return;
        }
        lectern.turnPage();

        if (!"beta".equals(displayText(helper, displayPos))) {
            helper.fail("Expected display to update after turning lectern page");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void multipleLightPipeSenders_garbleReceiverOutput(GameTestHelper helper) {
        BlockPos receiverPos = new BlockPos(3, 1, 4);
        BlockPos centerCable = new BlockPos(3, 1, 3);
        BlockPos leftSender = new BlockPos(2, 1, 3);
        BlockPos rightSender = new BlockPos(4, 1, 3);

        helper.setBlock(receiverPos, display(Direction.SOUTH));
        helper.setBlock(centerCable, lightPipe());
        helper.setBlock(leftSender, lectern(Direction.WEST));
        helper.setBlock(rightSender, lectern(Direction.EAST));

        setWritableBook(helper, leftSender, "alpha");
        if (!"alpha".equals(displayText(helper, receiverPos))) {
            helper.fail("Expected single sender to transmit clear text");
            return;
        }

        setWritableBook(helper, rightSender, "beta");
        TextDisplayBlockEntity display = textDisplayEntity(helper, receiverPos);
        if (display == null) {
            return;
        }

        String garbled = display.getDisplayText();
        if (garbled.length() != display.getMaxLength()) {
            helper.fail("Expected garbled collision text length " + display.getMaxLength() + ", got " + garbled.length());
            return;
        }
        if ("alpha".equals(garbled) || "beta".equals(garbled)) {
            helper.fail("Expected collision output to differ from either source");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dataCombinator_appendModeConcatenatesInputs(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(3, 1, 2);
        BlockPos combinatorPos = new BlockPos(3, 1, 3);
        BlockPos inputAPos = new BlockPos(2, 1, 3);
        BlockPos inputBPos = new BlockPos(4, 1, 3);

        helper.setBlock(outputPos, display(Direction.NORTH));
        helper.setBlock(combinatorPos, combinator(Direction.NORTH, DataCombinator.CombineMode.append));
        helper.setBlock(inputAPos, lectern(Direction.WEST));
        helper.setBlock(inputBPos, lectern(Direction.EAST));

        setWritableBook(helper, inputAPos, "Left ");
        setWritableBook(helper, inputBPos, "Right");

        if (!"Left Right".equals(displayText(helper, outputPos))) {
            helper.fail("Expected append combinator output to concatenate both inputs");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dataCombinator_replaceModeSubstitutesFirstPlaceholder(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(3, 1, 2);
        BlockPos combinatorPos = new BlockPos(3, 1, 3);
        BlockPos inputAPos = new BlockPos(2, 1, 3);
        BlockPos inputBPos = new BlockPos(4, 1, 3);

        helper.setBlock(outputPos, display(Direction.NORTH));
        helper.setBlock(combinatorPos, combinator(Direction.NORTH, DataCombinator.CombineMode.replace));
        helper.setBlock(inputAPos, lectern(Direction.WEST));
        helper.setBlock(inputBPos, lectern(Direction.EAST));

        setWritableBook(helper, inputAPos, "Hello %s and %s");
        setWritableBook(helper, inputBPos, "world");

        if (!"Hello world and %s".equals(displayText(helper, outputPos))) {
            helper.fail("Expected replace combinator output to substitute only the first placeholder");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dataCombinator_chainedCombinatorsPropagateToDownstreamOutput(GameTestHelper helper) {
        // Upstream combinator at (2,1,3) facing EAST -> output feeds the downstream combinator to its east.
        //   input A (north wing) = lectern (2,1,2), input B (south wing) = lectern (2,1,4)
        // Downstream combinator at (3,1,3) facing NORTH -> output (north) feeds the display at (3,1,2).
        //   input A (west wing) = upstream combinator (2,1,3), input B (east wing) = lectern (4,1,3)
        BlockPos outputPos = new BlockPos(3, 1, 2);
        BlockPos downstreamPos = new BlockPos(3, 1, 3);
        BlockPos upstreamPos = new BlockPos(2, 1, 3);
        BlockPos upstreamInputAPos = new BlockPos(2, 1, 2);
        BlockPos upstreamInputBPos = new BlockPos(2, 1, 4);
        BlockPos downstreamInputBPos = new BlockPos(4, 1, 3);

        helper.setBlock(outputPos, display(Direction.NORTH));
        helper.setBlock(downstreamPos, combinator(Direction.NORTH, DataCombinator.CombineMode.append));
        helper.setBlock(upstreamPos, combinator(Direction.EAST, DataCombinator.CombineMode.append));
        helper.setBlock(upstreamInputAPos, lectern(Direction.NORTH));
        helper.setBlock(upstreamInputBPos, lectern(Direction.SOUTH));
        helper.setBlock(downstreamInputBPos, lectern(Direction.EAST));

        setWritableBook(helper, upstreamInputAPos, "foo");
        setWritableBook(helper, upstreamInputBPos, "bar");
        setWritableBook(helper, downstreamInputBPos, "baz");

        // upstream = "foo" + "bar" = "foobar"; downstream = "foobar" + "baz" = "foobarbaz"
        String actual = displayText(helper, outputPos);
        if (!"foobarbaz".equals(actual)) {
            helper.fail("Expected chained combinator output \"foobarbaz\", got \"" + actual + "\"");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dataCombinator_chainedCombinatorUpdatesPropagateOnUpstreamInputChange(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(3, 1, 2);
        BlockPos downstreamPos = new BlockPos(3, 1, 3);
        BlockPos upstreamPos = new BlockPos(2, 1, 3);
        BlockPos upstreamInputAPos = new BlockPos(2, 1, 2);
        BlockPos upstreamInputBPos = new BlockPos(2, 1, 4);
        BlockPos downstreamInputBPos = new BlockPos(4, 1, 3);

        helper.setBlock(outputPos, display(Direction.NORTH));
        helper.setBlock(downstreamPos, combinator(Direction.NORTH, DataCombinator.CombineMode.append));
        helper.setBlock(upstreamPos, combinator(Direction.EAST, DataCombinator.CombineMode.append));
        helper.setBlock(upstreamInputAPos, lectern(Direction.NORTH));
        helper.setBlock(upstreamInputBPos, lectern(Direction.SOUTH));
        helper.setBlock(downstreamInputBPos, lectern(Direction.EAST));

        setWritableBook(helper, upstreamInputAPos, "foo");
        setWritableBook(helper, upstreamInputBPos, "bar");
        setWritableBook(helper, downstreamInputBPos, "baz");

        if (!"foobarbaz".equals(displayText(helper, outputPos))) {
            helper.fail("Expected initial chained combinator output \"foobarbaz\", got \"" + displayText(helper, outputPos) + "\"");
            return;
        }

        // Changing an upstream input must propagate all the way through to the downstream output.
        setWritableBook(helper, upstreamInputAPos, "QUX");
        helper.succeedWhen(() -> {
            String actual = displayText(helper, outputPos);
            if (!"QUXbarbaz".equals(actual)) {
                helper.fail("Expected updated chained combinator output \"QUXbarbaz\", got \"" + actual + "\"");
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void dataCombinator_reTriggeredInputReExecutesDownstreamSerialBus(GameTestHelper helper) {
        // lectern -> combinator -> serial bus -> target block.
        // Re-sending the same input produces an identical combined output, but the serial
        // bus must still re-execute its command. The combinator therefore has to forward
        // on every input trigger, not only when its output text changes.
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos combinatorPos = new BlockPos(3, 1, 4);
        BlockPos inputPos = new BlockPos(2, 1, 4);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(combinatorPos, combinator(Direction.NORTH, DataCombinator.CombineMode.append));
        helper.setBlock(inputPos, lectern(Direction.WEST));

        setWritableBook(helper, inputPos, "set_redstone 9");

        int afterFirst = busOutput(helper, serialBusPos);
        if (afterFirst != 9) {
            helper.fail("Expected combinator-fed serial bus to set redstone 9, got " + afterFirst);
            return;
        }

        // Clear the target, then re-send the identical input. The combined output does not
        // change, so a change-only combinator would leave the target at 0; a correct one
        // re-fires the serial bus and restores 9.
        SerialBusBlockEntity bus = serialBusEntity(helper, serialBusPos);
        if (bus == null) {
            return;
        }
        bus.setRedstoneOutput(0);
        setWritableBook(helper, inputPos, "set_redstone 9");

        helper.succeedWhen(() -> {
            int actual = busOutput(helper, serialBusPos);
            if (actual != 9) {
                helper.fail("Expected re-triggered input to re-execute serial bus (redstone 9), got " + actual);
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void dataCombinator_feedbackCycleDoesNotStackOverflow(GameTestHelper helper) {
        // Wire two combinators into a feedback cycle: A.out -> B.inA, B.out -> A.inA.
        //   combinatorA (2,1,2) facing SOUTH: out -> cable (2,1,3), inA (east) -> cable (3,1,2),
        //                                      inB (west) -> lectern (1,1,2)
        //   combinatorB (3,1,3) facing NORTH: out -> cable (3,1,2), inA (west) -> cable (2,1,3)
        // Seeding the cycle from the lectern must settle without recursing forever.
        BlockPos combinatorAPos = new BlockPos(2, 1, 2);
        BlockPos combinatorBPos = new BlockPos(3, 1, 3);
        BlockPos aOutToBInCable = new BlockPos(2, 1, 3);
        BlockPos bOutToAInCable = new BlockPos(3, 1, 2);
        BlockPos seedPos = new BlockPos(1, 1, 2);
        BlockPos observerPos = new BlockPos(4, 1, 2);

        helper.setBlock(combinatorAPos, combinator(Direction.SOUTH, DataCombinator.CombineMode.append));
        helper.setBlock(combinatorBPos, combinator(Direction.NORTH, DataCombinator.CombineMode.append));
        helper.setBlock(aOutToBInCable, lightPipe());
        helper.setBlock(bOutToAInCable, lightPipe());
        helper.setBlock(seedPos, lectern(Direction.WEST));
        helper.setBlock(observerPos, display(Direction.EAST));

        // If the re-entrancy guard is missing, this synchronous propagation recurses until
        // it throws StackOverflowError, failing the test.
        setWritableBook(helper, seedPos, "X");

        // The observer hangs off combinator B's output cable, so seeing the seed there proves
        // the cycle actually propagated (A -> B) rather than being silently disconnected.
        String observed = displayText(helper, observerPos);
        if (!observed.contains("X")) {
            helper.fail("Expected feedback cycle to propagate seed through the combinators, observed \"" + observed + "\"");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dataComparator_equalsModePowersLampForMatchingInputs(GameTestHelper helper) {
        BlockPos lampPos = new BlockPos(3, 1, 2);
        BlockPos comparatorPos = new BlockPos(3, 1, 3);
        BlockPos inputAPos = new BlockPos(4, 1, 3);
        BlockPos inputBPos = new BlockPos(2, 1, 3);

        helper.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(comparatorPos, comparator(Direction.EAST, DataComparator.ComparisonMode.equals));
        helper.setBlock(inputAPos, lectern(Direction.EAST));
        helper.setBlock(inputBPos, lectern(Direction.WEST));

        setWritableBook(helper, inputAPos, "match");
        setWritableBook(helper, inputBPos, "match");

        helper.assertBlockProperty(comparatorPos, DataComparator.POWERED, true);
        helper.assertBlockProperty(lampPos, RedstoneLampBlock.LIT, true);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void dataComparator_greaterThanModeParsesNumericPrefixesAndRejectsInvalidInput(GameTestHelper helper) {
        BlockPos lampPos = new BlockPos(3, 1, 2);
        BlockPos comparatorPos = new BlockPos(3, 1, 3);
        BlockPos inputAPos = new BlockPos(4, 1, 3);
        BlockPos inputBPos = new BlockPos(2, 1, 3);

        helper.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(comparatorPos, comparator(Direction.EAST, DataComparator.ComparisonMode.greater_than));
        helper.setBlock(inputAPos, lectern(Direction.EAST));
        helper.setBlock(inputBPos, lectern(Direction.WEST));

        setWritableBook(helper, inputAPos, "12 m");
        setWritableBook(helper, inputBPos, "2");
        helper.assertBlockProperty(comparatorPos, DataComparator.POWERED, true);
        helper.assertBlockProperty(lampPos, RedstoneLampBlock.LIT, true);

        setWritableBook(helper, inputAPos, "not_a_number");
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(comparatorPos, DataComparator.POWERED, false);
            helper.assertBlockProperty(lampPos, RedstoneLampBlock.LIT, false);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void dataTranscriber_writesIncomingTextToBookBelowWhenPowered(GameTestHelper helper) {
        BlockPos bookLecternPos = new BlockPos(3, 1, 3);
        BlockPos transcriberPos = new BlockPos(3, 2, 3);
        BlockPos powerPos = new BlockPos(3, 2, 2);
        BlockPos cablePos = new BlockPos(3, 2, 4);
        BlockPos senderPos = new BlockPos(3, 2, 5);

        helper.setBlock(bookLecternPos, lectern(Direction.NORTH));
        setWritableBook(helper, bookLecternPos);
        helper.setBlock(transcriberPos, transcriber(Direction.NORTH));
        helper.setBlock(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));
        setWritableBook(helper, senderPos, "printed log");

        helper.succeedWhen(() -> {
            String actual = currentPageText(helper, bookLecternPos);
            if (!"printed log".equals(actual)) {
                helper.fail("Expected transcriber to write received text, got \"" + actual + "\"");
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_executesReceivedScriptCommandAgainstFacingBlock(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos senderPos = new BlockPos(3, 1, 5);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));

        setWritableBook(helper, senderPos, "set_redstone 9");

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        if (!"set_redstone 9".equals(serialBus.getCurrentText())) {
            helper.fail("Expected serial bus to store the received command text");
            return;
        }

        int stored = busOutput(helper, serialBusPos);
        if (stored != 9) {
            helper.fail("Expected serial bus command to set redstone 9, got " + stored);
            return;
        }
        helper.succeed();
    }

    /**
     * The bus emits like a repeater pointed into the block it faces: that block is strongly
     * powered and lights a lamp beyond it, while a lamp beside the bus stays dark.
     */
    @GameTest(template = TEMPLATE)
    public static void serialBus_setRedstonePowersFacedBlockOnly(GameTestHelper helper) {
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos beyondTargetPos = new BlockPos(3, 1, 1);
        BlockPos besideBusPos = new BlockPos(2, 1, 3);
        helper.setBlock(beyondTargetPos, Blocks.REDSTONE_LAMP);
        helper.setBlock(besideBusPos, Blocks.REDSTONE_LAMP);

        if (feedSerialBus(helper, serialBusPos, "set_redstone 15") == null) {
            return;
        }

        helper.succeedWhen(() -> {
            helper.assertBlockProperty(beyondTargetPos, RedstoneLampBlock.LIT, true);
            helper.assertBlockProperty(besideBusPos, RedstoneLampBlock.LIT, false);
        });
    }

    /** A bus aimed at a Redstone Converter feeds it even when it is not on the converter's input face. */
    @GameTest(template = TEMPLATE)
    public static void serialBus_setRedstoneFeedsRedstoneConverterFromAnySide(GameTestHelper helper) {
        BlockPos converterPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos senderPos = new BlockPos(3, 1, 5);

        helper.setBlock(converterPos, ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, Direction.EAST));
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));

        setWritableBook(helper, senderPos, "set_redstone 11");

        helper.succeedWhen(() -> {
            if (!(helper.getBlockEntity(converterPos) instanceof RedstoneConverterBlockEntity converter)) {
                helper.fail("Expected a redstone converter at " + converterPos);
                return;
            }
            int supplied = converter.getCurrentSuppliedSignal(0);
            if (supplied != 11) {
                helper.fail("Expected the converter to take 11 from the bus, got " + supplied);
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_recordsSuccessfulCommand(GameTestHelper helper) {
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        SerialBusBlockEntity serialBus = feedSerialBus(helper, serialBusPos, "set_redstone 9");
        if (serialBus == null) {
            return;
        }
        if (serialBus.getLastOutcome() != SerialBusBlockEntity.Outcome.SUCCESS) {
            helper.fail("Expected a successful outcome, got " + serialBus.getLastOutcome()
                    + " (" + serialBus.getLastFailure() + ")");
            return;
        }
        if (!"set_redstone 9".equals(serialBus.getLastCommand())) {
            helper.fail("Expected the last command to be recorded, got \"" + serialBus.getLastCommand() + "\"");
            return;
        }
        if (serialBus.getLastFailure() != null) {
            helper.fail("Expected no failure on success, got " + serialBus.getLastFailure());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_recordsUnknownCommandWithItsLocation(GameTestHelper helper) {
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        SerialBusBlockEntity serialBus = feedSerialBus(helper, serialBusPos, "frobnicate 9");
        if (serialBus == null) {
            return;
        }
        ScriptCommandFailure failure = serialBus.getLastFailure();
        if (serialBus.getLastOutcome() != SerialBusBlockEntity.Outcome.FAILURE || failure == null) {
            helper.fail("Expected an unknown command to be recorded as a failure, got " + serialBus.getLastOutcome());
            return;
        }
        if (!"Unknown command 'frobnicate'".equals(failure.reason())) {
            helper.fail("Expected a plain-language reason, got \"" + failure.reason() + "\"");
            return;
        }
        if (!failure.faultInCommand() || failure.faultStart() != 0 || failure.faultEnd() != "frobnicate".length()) {
            helper.fail("Expected the fault to mark 'frobnicate', got " + failure);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_recordsBadArgumentWithItsLocation(GameTestHelper helper) {
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        SerialBusBlockEntity serialBus = feedSerialBus(helper, serialBusPos, "set_redstone banana");
        if (serialBus == null) {
            return;
        }
        ScriptCommandFailure failure = serialBus.getLastFailure();
        if (serialBus.getLastOutcome() != SerialBusBlockEntity.Outcome.FAILURE || failure == null) {
            helper.fail("Expected a bad argument to be recorded as a failure, got " + serialBus.getLastOutcome());
            return;
        }
        if (failure.reason().isBlank() || failure.reason().contains("Exception")) {
            helper.fail("Expected a plain-language reason, got \"" + failure.reason() + "\"");
            return;
        }
        int bananaAt = "set_redstone banana".indexOf("banana");
        if (!failure.faultInCommand() || failure.faultStart() != bananaAt) {
            helper.fail("Expected the fault to mark 'banana', got " + failure);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_explainsValueOfTypeMismatch(GameTestHelper helper) {
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        String command = "set_redstone value_of(dimension)";
        SerialBusBlockEntity serialBus = feedSerialBus(helper, serialBusPos, command);
        if (serialBus == null) {
            return;
        }
        ScriptCommandFailure failure = serialBus.getLastFailure();
        if (failure == null) {
            helper.fail("Expected a type mismatch to be recorded as a failure, got " + serialBus.getLastOutcome());
            return;
        }
        String expected = "value_of(dimension) gives dimension, but set_redstone needs int";
        if (!expected.equals(failure.reason())) {
            helper.fail("Expected a reason naming both types, got \"" + failure.reason() + "\"");
            return;
        }
        int tokenAt = command.indexOf("value_of(");
        if (!failure.faultInCommand() || failure.faultStart() != tokenAt || failure.faultEnd() != command.length()) {
            helper.fail("Expected the fault to mark the value_of token, got " + failure);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_explainsValueOfBadFollowOn(GameTestHelper helper) {
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        SerialBusBlockEntity serialBus = feedSerialBus(helper, serialBusPos, "set_redstone value_of(dimension frob)");
        if (serialBus == null) {
            return;
        }
        ScriptCommandFailure failure = serialBus.getLastFailure();
        if (failure == null) {
            helper.fail("Expected a bad follow-on word to be recorded as a failure, got " + serialBus.getLastOutcome());
            return;
        }
        String expected = "In value_of(dimension frob), 'frob' cannot follow dimension";
        if (!expected.equals(failure.reason())) {
            helper.fail("Expected the reason to name the stray word, got \"" + failure.reason() + "\"");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_explainsUnknownValueOfGetter(GameTestHelper helper) {
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        SerialBusBlockEntity serialBus = feedSerialBus(helper, serialBusPos, "set_redstone value_of(frobnicate)");
        if (serialBus == null) {
            return;
        }
        ScriptCommandFailure failure = serialBus.getLastFailure();
        if (failure == null) {
            helper.fail("Expected an unknown getter to be recorded as a failure, got " + serialBus.getLastOutcome());
            return;
        }
        if (!"'frobnicate' is not a known value in value_of(frobnicate)".equals(failure.reason())) {
            helper.fail("Expected the reason to name the unknown value, got \"" + failure.reason() + "\"");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_getModeDoesNotExecute(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos senderPos = new BlockPos(3, 1, 5);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        serialBus.setMode(SerialBusMode.GET);
        setWritableBook(helper, senderPos, "set_redstone 9");

        int stored = busOutput(helper, serialBusPos);
        if (stored != 0) {
            helper.fail("Expected a bus in Get mode to leave the target alone, got redstone " + stored);
            return;
        }
        if (serialBus.getLastOutcome() != SerialBusBlockEntity.Outcome.NONE) {
            helper.fail("Expected a bus in Get mode to record nothing, got " + serialBus.getLastOutcome());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_getModeSendsEvaluatedValue(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos displayPos = new BlockPos(3, 1, 5);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(displayPos, display(Direction.SOUTH));

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        serialBus.setMode(SerialBusMode.GET);
        serialBus.setExpression("pos as_string");

        BlockPos absTarget = helper.absolutePos(targetPos);
        String expected = absTarget.getX() + " " + absTarget.getY() + " " + absTarget.getZ();
        helper.succeedWhen(() -> {
            String shown = displayText(helper, displayPos);
            if (!expected.equals(shown)) {
                helper.fail("Expected the bus to send \"" + expected + "\", display shows \"" + shown
                        + "\" (outcome " + serialBus.getLastOutcome()
                        + ", reason " + (serialBus.getLastFailure() == null ? "none" : serialBus.getLastFailure().reason()) + ")");
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_getModeAddsAsStringWhenTheChainStopsShort(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos displayPos = new BlockPos(3, 1, 5);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(displayPos, display(Direction.SOUTH));

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        serialBus.setMode(SerialBusMode.GET);
        // A block position, not text: the bus should finish the chain off itself.
        serialBus.setExpression("pos");

        BlockPos absTarget = helper.absolutePos(targetPos);
        String expected = absTarget.getX() + " " + absTarget.getY() + " " + absTarget.getZ();
        helper.succeedWhen(() -> {
            String shown = displayText(helper, displayPos);
            if (!expected.equals(shown)) {
                helper.fail("Expected \"pos\" to send \"" + expected + "\", display shows \"" + shown
                        + "\" (outcome " + serialBus.getLastOutcome()
                        + ", reason " + (serialBus.getLastFailure() == null ? "none" : serialBus.getLastFailure().reason()) + ")");
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_getModeFailureClearsWhatItSent(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos displayPos = new BlockPos(3, 1, 5);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(displayPos, display(Direction.SOUTH));

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        serialBus.setMode(SerialBusMode.GET);
        serialBus.setExpression("pos as_string");

        BlockPos absTarget = helper.absolutePos(targetPos);
        String expected = absTarget.getX() + " " + absTarget.getY() + " " + absTarget.getZ();

        // Once a good value is on the pipe, a broken chain takes it away rather than leaving a
        // reading that nothing is refreshing.
        helper.runAtTickTime(10L, () -> {
            if (!expected.equals(displayText(helper, displayPos))) {
                helper.fail("Expected the good value to reach the display first");
                return;
            }
            serialBus.setExpression("not_a_getter at_all");
        });
        helper.runAtTickTime(25L, () -> {
            if (serialBus.getLastOutcome() != SerialBusBlockEntity.Outcome.FAILURE) {
                helper.fail("Expected a broken chain to record a failure, got " + serialBus.getLastOutcome());
                return;
            }
            if (serialBus.getLastFailure() == null) {
                helper.fail("Expected a reason for the failure");
                return;
            }
            String shown = displayText(helper, displayPos);
            if (!shown.isEmpty()) {
                helper.fail("Expected a broken chain to clear the display, it shows \"" + shown + "\"");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_getModeClearsWhenTheExpressionIsEmptied(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos displayPos = new BlockPos(3, 1, 5);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(displayPos, display(Direction.SOUTH));

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        serialBus.setMode(SerialBusMode.GET);
        serialBus.setExpression("pos as_string");

        helper.runAtTickTime(10L, () -> {
            if (displayText(helper, displayPos).isEmpty()) {
                helper.fail("Expected the bus to have sent something before the expression is cleared");
                return;
            }
            serialBus.setExpression("");
        });
        helper.runAtTickTime(25L, () -> {
            String shown = displayText(helper, displayPos);
            if (!shown.isEmpty()) {
                helper.fail("Expected a bus with nothing to read to clear the display, it shows \"" + shown + "\"");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_leavingGetModeClearsWhatItSent(GameTestHelper helper) {
        BlockPos targetPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos displayPos = new BlockPos(3, 1, 5);

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(displayPos, display(Direction.SOUTH));

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        serialBus.setMode(SerialBusMode.GET);
        serialBus.setExpression("pos as_string");

        helper.runAtTickTime(10L, () -> {
            if (displayText(helper, displayPos).isEmpty()) {
                helper.fail("Expected the bus to have sent something before switching back");
                return;
            }
            serialBus.setMode(SerialBusMode.EXECUTE);
        });
        // The display holds its text a little after the pipe goes blank.
        helper.runAtTickTime(11L + TextDisplayBlockEntity.CLEAR_HOLD_TICKS, () -> {
            String shown = displayText(helper, displayPos);
            if (!shown.isEmpty()) {
                helper.fail("Expected the display to clear when the bus stopped sending, it shows \"" + shown + "\"");
                return;
            }
            helper.succeed();
        });
    }

    /** Stone target, bus facing it, a pipe, and a lectern holding the command. */
    private static SerialBusBlockEntity feedSerialBus(GameTestHelper helper, BlockPos serialBusPos, String command) {
        BlockPos targetPos = serialBusPos.north();
        BlockPos cablePos = serialBusPos.south();
        BlockPos senderPos = cablePos.south();

        helper.setBlock(targetPos, Blocks.STONE.defaultBlockState());
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));

        setWritableBook(helper, senderPos, command);
        return serialBusEntity(helper, serialBusPos);
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_setDisplayTextUpdatesManualDisplayText(GameTestHelper helper) {
        BlockPos displayLinkPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos senderPos = new BlockPos(3, 1, 5);

        helper.setBlock(displayLinkPos, displayLink(Direction.NORTH));
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));

        DisplayLinkBlockEntity displayLinkEntity = displayLinkEntity(helper, displayLinkPos);
        if (displayLinkEntity == null) {
            return;
        }
        displayLinkEntity.activeSource = CreateCompat.SERIAL_BUS_MANUAL_SOURCE.get();

        setWritableBook(helper, senderPos, "set_display_text \"manual text\"");

        SerialBusBlockEntity serialBus = serialBusEntity(helper, serialBusPos);
        if (serialBus == null) {
            return;
        }
        if (!"set_display_text \"manual text\"".equals(serialBus.getCurrentText())) {
            helper.fail("Expected serial bus to keep received command text for the default display source");
            return;
        }
        BlockEntity displayLink = helper.getBlockEntity(displayLinkPos);
        if (!(displayLink instanceof DisplayLinkManualTextAccessor accessor)) {
            helper.fail("Expected display link to expose manual text accessor, got " + displayLink);
            return;
        }
        if (!"manual text".equals(accessor.zps$getManualDisplayText())) {
            helper.fail("Expected set_display_text to update display link manual text, got \"" + accessor.zps$getManualDisplayText() + "\"");
            return;
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_passiveDisplaySourceSuppressesScriptCommands(GameTestHelper helper) {
        BlockPos displayLinkPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos senderPos = new BlockPos(3, 1, 5);

        helper.setBlock(displayLinkPos, displayLink(Direction.NORTH));
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));

        DisplayLinkBlockEntity displayLinkEntity = displayLinkEntity(helper, displayLinkPos);
        if (displayLinkEntity == null) {
            return;
        }
        displayLinkEntity.activeSource = CreateCompat.SERIAL_BUS_SOURCE.get();

        setWritableBook(helper, senderPos, "set_redstone 9");

        int stored = busOutput(helper, serialBusPos);
        if (stored != 0) {
            helper.fail("Expected passive Serial Bus display source to suppress script command execution, got redstone " + stored);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serialBus_displayLinkNotFacingBusDoesNotSuppressScriptCommands(GameTestHelper helper) {
        BlockPos displayLinkPos = new BlockPos(3, 1, 2);
        BlockPos serialBusPos = new BlockPos(3, 1, 3);
        BlockPos cablePos = new BlockPos(3, 1, 4);
        BlockPos senderPos = new BlockPos(3, 1, 5);

        helper.setBlock(displayLinkPos, displayLink(Direction.SOUTH));
        helper.setBlock(serialBusPos, serialBus(Direction.NORTH));
        helper.setBlock(cablePos, lightPipe());
        helper.setBlock(senderPos, lectern(Direction.SOUTH));

        DisplayLinkBlockEntity displayLinkEntity = displayLinkEntity(helper, displayLinkPos);
        if (displayLinkEntity == null) {
            return;
        }
        displayLinkEntity.activeSource = CreateCompat.SERIAL_BUS_SOURCE.get();

        setWritableBook(helper, senderPos, "set_redstone 9");

        int stored = busOutput(helper, serialBusPos);
        if (stored != 9) {
            helper.fail("Expected display link not sourcing from serial bus to allow script command execution, got redstone " + stored);
            return;
        }
        helper.succeed();
    }
}
