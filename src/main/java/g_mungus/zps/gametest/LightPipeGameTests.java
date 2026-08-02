package g_mungus.zps.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.DataCombinator;
import g_mungus.zps.block.cableNetwork.light_pipe.DataLecternBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.blockentity.light_pipe.DataLecternBlockEntity;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import g_mungus.zps.compat.create.CreateCompat;
import g_mungus.zps.compat.create.DisplayLinkManualTextAccessor;
import g_mungus.zps.commands.content.executors.SetRedstoneCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Forge Gametests for the light pipe data combinator, focused on chaining behaviour.
 *
 * Structure template: data/zps/structures/gametest/flat_7x4x7.nbt
 * Stone floor at y=0, 3 blocks of air above. Test blocks placed at y=1.
 *
 * Combinator wiring (FACING points at the output; inputs sit on the two wings):
 *   output (TRIPLE_C) -> relative(FACING)
 *   input A (TRIPLE_A) -> relative(FACING.counterClockWise)
 *   input B (TRIPLE_B) -> relative(FACING.clockWise)
 * A data lectern connects on its back (opposite its FACING).
 */
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

    private static BlockState serialBus(Direction facing) {
        return ModBlocks.SERIAL_BUS.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, facing);
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

        int afterFirst = SetRedstoneCommand.getRedstonePowerAt(helper.getLevel(), helper.absolutePos(targetPos));
        if (afterFirst != 9) {
            helper.fail("Expected combinator-fed serial bus to set redstone 9, got " + afterFirst);
            return;
        }

        // Clear the target, then re-send the identical input. The combined output does not
        // change, so a change-only combinator would leave the target at 0; a correct one
        // re-fires the serial bus and restores 9.
        SetRedstoneCommand.setRedstone(helper.getLevel(), helper.absolutePos(targetPos), 0);
        setWritableBook(helper, inputPos, "set_redstone 9");

        helper.succeedWhen(() -> {
            int actual = SetRedstoneCommand.getRedstonePowerAt(helper.getLevel(), helper.absolutePos(targetPos));
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

        int stored = SetRedstoneCommand.getRedstonePowerAt(helper.getLevel(), helper.absolutePos(displayLinkPos));
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

        int stored = SetRedstoneCommand.getRedstonePowerAt(helper.getLevel(), helper.absolutePos(displayLinkPos));
        if (stored != 9) {
            helper.fail("Expected display link not sourcing from serial bus to allow script command execution, got redstone " + stored);
            return;
        }
        helper.succeed();
    }
}
