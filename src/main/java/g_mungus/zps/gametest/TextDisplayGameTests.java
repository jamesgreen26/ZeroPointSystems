package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.light_pipe.DisplayGroup;
import g_mungus.zps.block.cableNetwork.light_pipe.DisplayLayout;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/**
 * Gametests for text display merging, dyes and font size.
 *
 * Structure template: data/zps/structures/gametest/flat_7x4x7.nbt
 * Stone floor at y=0, 3 blocks of air above. Test blocks placed at y=1 and up.
 *
 * {@code @PrefixGameTestTemplate(false)} disables the default "classname." prefix
 * so all methods share the single flat platform structure.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TextDisplayGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static BlockState display(Direction facing) {
        return ModBlocks.TEXT_DISPLAY.get().defaultBlockState()
                .setValue(TextDisplayBlock.FACING, facing);
    }

    /** Asserts the display at {@code pos} sits at {@code column, row} of a {@code width x height} display. */
    private static void assertGroup(GameTestHelper helper, BlockPos pos, int width, int height, int column, int row) {
        DisplayLayout layout = helper.getBlockState(pos).getValue(TextDisplayBlock.LAYOUT);
        int[] actual = {layout.width, layout.height, layout.column, layout.row};
        int[] expected = {width, height, column, row};
        if (!Arrays.equals(actual, expected)) {
            throw new GameTestAssertException("Display at " + pos + " should be " + width + "x" + height
                    + " at " + column + "," + row + ", was " + actual[0] + "x" + actual[1]
                    + " at " + actual[2] + "," + actual[3]);
        }
    }

    /** Every display in the area agrees with every other member of its display about where they sit. */
    private static void assertGroupsConsistent(GameTestHelper helper) {
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 7; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = helper.getBlockState(pos);
                    if (!state.is(ModBlocks.TEXT_DISPLAY.get())) {
                        continue;
                    }
                    // The template is never rotated, so relative positions work as world ones.
                    DisplayGroup group = DisplayGroup.of(state, pos);
                    for (int row = 0; row < group.height(); row++) {
                        for (int column = 0; column < group.width(); column++) {
                            BlockPos member = group.cell(column, row);
                            if (!helper.getBlockState(member).is(ModBlocks.TEXT_DISPLAY.get())) {
                                throw new GameTestAssertException("Display at " + pos + " claims missing member " + member);
                            }
                            assertGroup(helper, member, group.width(), group.height(), column, row);
                        }
                    }
                }
            }
        }
    }

    // FACING = NORTH throughout: the viewer stands north looking south, so their
    // right is west (-X). Column 0 is the east-most block, row 0 the top.

    /** A lone display is a 1x1 display. */
    @GameTest(template = TEMPLATE)
    public static void singleBlock_isOneByOne(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, display(Direction.NORTH));

        assertGroup(helper, pos, 1, 1, 0, 0);
        helper.succeed();
    }

    /** Three in a row, placed left to right, make one 3x1 display. */
    @GameTest(template = TEMPLATE)
    public static void rowOfThree_mergesThreeByOne(GameTestHelper helper) {
        for (int x = 4; x >= 2; x--) {
            helper.setBlock(new BlockPos(x, 1, 3), display(Direction.NORTH));
        }
        for (int column = 0; column < 3; column++) {
            assertGroup(helper, new BlockPos(4 - column, 1, 3), 3, 1, column, 0);
        }
        helper.succeed();
    }

    /** A row of four is too long for one display: three merge and the fourth stands alone. */
    @GameTest(template = TEMPLATE)
    public static void rowOfFour_capsAtThree(GameTestHelper helper) {
        for (int x = 4; x >= 1; x--) {
            helper.setBlock(new BlockPos(x, 1, 3), display(Direction.NORTH));
        }
        for (int column = 0; column < 3; column++) {
            assertGroup(helper, new BlockPos(4 - column, 1, 3), 3, 1, column, 0);
        }
        assertGroup(helper, new BlockPos(1, 1, 3), 1, 1, 0, 0);
        helper.succeed();
    }

    /** Two stacked displays make a 1x2 display. */
    @GameTest(template = TEMPLATE)
    public static void twoStacked_mergeOneByTwo(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(3, 1, 3);
        BlockPos top = bottom.above();
        helper.setBlock(bottom, display(Direction.NORTH));
        helper.setBlock(top, display(Direction.NORTH));

        assertGroup(helper, top, 1, 2, 0, 0);
        assertGroup(helper, bottom, 1, 2, 0, 1);
        helper.succeed();
    }

    /** A full 3x3 wall merges into one display whatever order it is built in. */
    @GameTest(template = TEMPLATE)
    public static void threeByThree_anyOrderMergesWhole(GameTestHelper helper) {
        // A fixed scramble of the 9 cells, as (column, row).
        int[][] order = {{2, 1}, {0, 2}, {1, 1}, {2, 0}, {0, 0}, {1, 2}, {2, 2}, {0, 1}, {1, 0}};
        for (int[] cell : order) {
            helper.setBlock(new BlockPos(4 - cell[0], 3 - cell[1], 3), display(Direction.NORTH));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                assertGroup(helper, new BlockPos(4 - column, 3 - row, 3), 3, 3, column, row);
            }
        }
        helper.succeed();
    }

    /** A 3x2 wall built in reading order merges into one 3x2 display. */
    @GameTest(template = TEMPLATE)
    public static void threeByTwo_mergesWhole(GameTestHelper helper) {
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                helper.setBlock(new BlockPos(4 - column, 2 - row, 3), display(Direction.NORTH));
            }
        }
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                assertGroup(helper, new BlockPos(4 - column, 2 - row, 3), 3, 2, column, row);
            }
        }
        helper.succeed();
    }

    /** A block placed beside a finished display does not split it to fit a different shape. */
    @GameTest(template = TEMPLATE)
    public static void neighbourDoesNotSplitDisplay(GameTestHelper helper) {
        for (BlockPos p : new BlockPos[]{new BlockPos(3, 1, 3), new BlockPos(2, 1, 3),
                new BlockPos(3, 2, 3), new BlockPos(2, 2, 3)}) {
            helper.setBlock(p, display(Direction.NORTH));
        }
        BlockPos side = new BlockPos(1, 2, 3);
        helper.setBlock(side, display(Direction.NORTH));

        assertGroup(helper, new BlockPos(3, 2, 3), 2, 2, 0, 0);
        assertGroup(helper, new BlockPos(2, 1, 3), 2, 2, 1, 1);
        assertGroup(helper, side, 1, 1, 0, 0);
        helper.succeed();
    }

    /** Three of a 2x2: the first pair merges and the third is left on its own. */
    @GameTest(template = TEMPLATE)
    public static void lShape_keepsFirstPair(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 1, 3);
        BlockPos b = new BlockPos(2, 1, 3); // right of a
        BlockPos c = new BlockPos(3, 2, 3); // above a
        helper.setBlock(a, display(Direction.NORTH));
        helper.setBlock(b, display(Direction.NORTH));
        helper.setBlock(c, display(Direction.NORTH));

        assertGroup(helper, a, 2, 1, 0, 0);
        assertGroup(helper, b, 2, 1, 1, 0);
        assertGroup(helper, c, 1, 1, 0, 0);
        helper.succeed();
    }

    /** Displays facing different ways never merge. */
    @GameTest(template = TEMPLATE)
    public static void differentFacing_doesNotMerge(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 1, 3);
        BlockPos b = new BlockPos(2, 1, 3);
        helper.setBlock(a, display(Direction.NORTH));
        helper.setBlock(b, display(Direction.SOUTH));

        assertGroup(helper, a, 1, 1, 0, 0);
        assertGroup(helper, b, 1, 1, 0, 0);
        helper.succeed();
    }

    /** Breaking the second block of a 4x1 leaves a single and a 2x1 behind. */
    @GameTest(template = TEMPLATE)
    public static void breakingMiddle_splitsRow(GameTestHelper helper) {
        for (int x = 4; x >= 1; x--) {
            helper.setBlock(new BlockPos(x, 1, 3), display(Direction.NORTH));
        }
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);

        assertGroup(helper, new BlockPos(4, 1, 3), 1, 1, 0, 0);
        assertGroup(helper, new BlockPos(2, 1, 3), 2, 1, 0, 0);
        assertGroup(helper, new BlockPos(1, 1, 3), 2, 1, 1, 0);
        helper.succeed();
    }

    /** Breaking the middle of a 3x3 regroups the rest into whole, consistent displays, and replacing it restores the 3x3. */
    @GameTest(template = TEMPLATE)
    public static void breakingInsideThreeByThree_regroupsAndRestores(GameTestHelper helper) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                helper.setBlock(new BlockPos(4 - column, 3 - row, 3), display(Direction.NORTH));
            }
        }
        BlockPos hole = new BlockPos(3, 2, 3); // column 1, row 1
        helper.setBlock(hole, Blocks.AIR);
        assertGroupsConsistent(helper);
        assertGroup(helper, new BlockPos(4, 3, 3), 3, 1, 0, 0);

        helper.setBlock(hole, display(Direction.NORTH));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                assertGroup(helper, new BlockPos(4 - column, 3 - row, 3), 3, 3, column, row);
            }
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Dye
    // -------------------------------------------------------------------------

    private static InteractionResult useOn(GameTestHelper helper, BlockPos pos, ItemStack stack, Player player) {
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = helper.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static Player creativePlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = true;
        return player;
    }

    private static DyeColor colorAt(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getBlockEntity(pos) instanceof TextDisplayBlockEntity display)) {
            throw new GameTestAssertException("No text display block entity at " + pos);
        }
        return display.getTextColor();
    }

    /** A fresh display renders white; using a dye on it stores that dye's colour and consumes the dye. */
    @GameTest(template = TEMPLATE)
    public static void dye_setsTextColourAndConsumesDye(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, display(Direction.NORTH));
        helper.assertTrue(colorAt(helper, pos) == DyeColor.WHITE, "Default text colour should be white");

        Player player = helper.makeMockSurvivalPlayer();
        ItemStack dye = new ItemStack(Items.RED_DYE, 2);
        InteractionResult result = useOn(helper, pos, dye, player);

        helper.assertTrue(result.consumesAction(), "Dye use should be consumed, got " + result);
        helper.assertTrue(colorAt(helper, pos) == DyeColor.RED, "Text colour should be red");
        helper.assertTrue(dye.getCount() == 1, "One dye should be consumed, stack has " + dye.getCount());
        helper.succeed();
    }

    /** Applying the same colour twice does nothing and keeps the dye. */
    @GameTest(template = TEMPLATE)
    public static void dye_sameColourIsNotConsumed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, display(Direction.NORTH));

        Player player = helper.makeMockSurvivalPlayer();
        ItemStack dye = new ItemStack(Items.WHITE_DYE, 1);
        useOn(helper, pos, dye, player);

        helper.assertTrue(colorAt(helper, pos) == DyeColor.WHITE, "Text colour should stay white");
        helper.assertTrue(dye.getCount() == 1, "Dye should not be consumed when the colour is unchanged");
        helper.succeed();
    }

    /** Dyeing one block of a merged 2×2 display recolours every block in it. */
    @GameTest(template = TEMPLATE)
    public static void dye_recoloursWholeMergedDisplay(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 2, 3);
        BlockPos b = new BlockPos(4, 2, 3);
        BlockPos c = new BlockPos(3, 1, 3);
        BlockPos d = new BlockPos(4, 1, 3);
        BlockPos lone = new BlockPos(1, 1, 3); // not part of the 2×2
        for (BlockPos p : new BlockPos[]{a, b, c, d, lone}) {
            helper.setBlock(p, display(Direction.NORTH));
        }
        assertGroup(helper, a, 2, 2, 1, 0);

        Player player = creativePlayer(helper);
        ItemStack dye = new ItemStack(Items.LIME_DYE, 1);
        useOn(helper, d, dye, player);

        for (BlockPos p : new BlockPos[]{a, b, c, d}) {
            helper.assertTrue(colorAt(helper, p) == DyeColor.LIME, "Merged display block at " + p + " should be lime");
        }
        helper.assertTrue(colorAt(helper, lone) == DyeColor.WHITE, "Unrelated display should stay white");
        helper.assertTrue(dye.getCount() == 1, "Creative players should not consume the dye");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Font size
    // -------------------------------------------------------------------------

    private static InteractionResult useEmptyHand(GameTestHelper helper, BlockPos pos, Player player) {
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        return helper.getBlockState(pos).use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    private static boolean isLargeAt(GameTestHelper helper, BlockPos pos) {
        if (!(helper.getBlockEntity(pos) instanceof TextDisplayBlockEntity display)) {
            throw new GameTestAssertException("No text display block entity at " + pos);
        }
        return display.isLargeText();
    }

    /** Shift-using a merged 2×2 display with an empty hand cycles every block's font size, and back. */
    @GameTest(template = TEMPLATE)
    public static void fontSize_shiftUseCyclesWholeMergedDisplay(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 2, 3);
        BlockPos b = new BlockPos(4, 2, 3);
        BlockPos c = new BlockPos(3, 1, 3);
        BlockPos d = new BlockPos(4, 1, 3);
        BlockPos lone = new BlockPos(1, 1, 3); // not part of the 2×2
        for (BlockPos p : new BlockPos[]{a, b, c, d, lone}) {
            helper.setBlock(p, display(Direction.NORTH));
        }
        helper.assertTrue(!isLargeAt(helper, a), "Default font size should be 1x");

        Player player = helper.makeMockPlayer();
        player.setShiftKeyDown(true);
        InteractionResult result = useEmptyHand(helper, d, player);
        helper.assertTrue(result.consumesAction(), "Shift-use should be consumed, got " + result);
        for (BlockPos p : new BlockPos[]{a, b, c, d}) {
            helper.assertTrue(isLargeAt(helper, p), "Merged display block at " + p + " should be 2x");
        }
        helper.assertTrue(!isLargeAt(helper, lone), "Unrelated display should stay 1x");

        useEmptyHand(helper, a, player);
        for (BlockPos p : new BlockPos[]{a, b, c, d}) {
            helper.assertTrue(!isLargeAt(helper, p), "Merged display block at " + p + " should be back to 1x");
        }
        helper.succeed();
    }

    /** Using the display with an empty hand without sneaking leaves the font size alone. */
    @GameTest(template = TEMPLATE)
    public static void fontSize_plainUseDoesNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, display(Direction.NORTH));

        useEmptyHand(helper, pos, helper.makeMockPlayer());
        helper.assertTrue(!isLargeAt(helper, pos), "Font size should stay 1x without sneaking");
        helper.succeed();
    }
}
