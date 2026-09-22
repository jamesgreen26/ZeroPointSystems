package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.light_pipe.DisplayLayout;
import g_mungus.zps.block.cableNetwork.light_pipe.TextDisplayBlock;
import g_mungus.zps.blockentity.light_pipe.TextDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Forge Gametests for {@link TextDisplayBlock#updateShape}.
 *
 * Coordinate system (all tests use FACING = NORTH):
 *   facing normal = (0, 0, -1)
 *   left          = facing × UP = (1, 0, 0)  →  east (+X)
 *
 * Structure template: data/zps/structure/gametest/flat_7x4x7.nbt
 * Stone floor at y=0, 3 blocks of air above. Test blocks placed at y=1.
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

    // -------------------------------------------------------------------------
    // Single block
    // -------------------------------------------------------------------------

    /** A lone TextDisplayBlock should have layout SINGLE_1x1. */
    @GameTest(template = TEMPLATE)
    public static void singleBlock_isSingleLayout(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, display(Direction.NORTH));

        helper.assertBlockProperty(pos, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Horizontal pair  (FACING=NORTH → left = east/+X)
    // -------------------------------------------------------------------------

    /**
     * Two blocks placed side-by-side horizontally.
     *   A at (3,1,3), B at (4,1,3)  →  B is to A's left (east)
     *   A → RIGHT_2x1  (has a block on its left/east side)
     *   B → LEFT_2x1   (has a block on its right/west side)
     */
    @GameTest(template = TEMPLATE)
    public static void twoBlocksHorizontal_mergeCorrectly(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3);
        BlockPos posB = new BlockPos(4, 1, 3); // posA.east()
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));

        helper.assertBlockProperty(posA, TextDisplayBlock.LAYOUT, DisplayLayout.RIGHT_2x1);
        helper.assertBlockProperty(posB, TextDisplayBlock.LAYOUT, DisplayLayout.LEFT_2x1);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Vertical pair
    // -------------------------------------------------------------------------

    /**
     * Two blocks stacked vertically.
     *   A at (3,1,3), B at (3,2,3)  →  B is above A
     *   A → BOTTOM_1x2  (has a block above it)
     *   B → TOP_1x2     (has a block below it)
     */
    @GameTest(template = TEMPLATE)
    public static void twoBlocksVertical_mergeCorrectly(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3);
        BlockPos posB = new BlockPos(3, 2, 3); // posA.above()
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));

        helper.assertBlockProperty(posA, TextDisplayBlock.LAYOUT, DisplayLayout.BOTTOM_1x2);
        helper.assertBlockProperty(posB, TextDisplayBlock.LAYOUT, DisplayLayout.TOP_1x2);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // 2×2 square
    // -------------------------------------------------------------------------

    /**
     * Four blocks forming a 2×2 square.
     *   A at (3,1,3)  B at (4,1,3)   ← bottom row
     *   C at (3,2,3)  D at (4,2,3)   ← top row
     *
     *   A → BOTTOM_RIGHT_2x2
     *   B → BOTTOM_LEFT_2x2
     *   C → TOP_RIGHT_2x2
     *   D → TOP_LEFT_2x2
     */
    @GameTest(template = TEMPLATE)
    public static void fourBlocksSquare_merge2x2(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3);
        BlockPos posB = new BlockPos(4, 1, 3); // posA.east()
        BlockPos posC = new BlockPos(3, 2, 3); // posA.above()
        BlockPos posD = new BlockPos(4, 2, 3); // posA.east().above()
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));
        helper.setBlock(posC, display(Direction.NORTH));
        helper.setBlock(posD, display(Direction.NORTH));

        helper.assertBlockProperty(posA, TextDisplayBlock.LAYOUT, DisplayLayout.BOTTOM_RIGHT_2x2);
        helper.assertBlockProperty(posB, TextDisplayBlock.LAYOUT, DisplayLayout.BOTTOM_LEFT_2x2);
        helper.assertBlockProperty(posC, TextDisplayBlock.LAYOUT, DisplayLayout.TOP_RIGHT_2x2);
        helper.assertBlockProperty(posD, TextDisplayBlock.LAYOUT, DisplayLayout.TOP_LEFT_2x2);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // L-shape (3 of 4 in a 2×2 square)
    // -------------------------------------------------------------------------

    /**
     * Three blocks in an L-shape (missing top-left corner).
     *   A at (3,1,3)  B at (4,1,3)   ← bottom row
     *   C at (3,2,3)                  ← top row (D missing)
     *
     * No 2×2 is possible, so each block falls back to a 2×1 or 1×2 layout.
     * A is adjacent to both B (east) and C (above) but the consistency loop
     * must pick only one; in practice A merges with B into a horizontal 2×1
     * and C stays SINGLE_1x1.
     */
    @GameTest(template = TEMPLATE)
    public static void threeBlocksLShape_noTwoByTwo(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3);
        BlockPos posB = new BlockPos(4, 1, 3); // posA.east()
        BlockPos posC = new BlockPos(3, 2, 3); // posA.above()
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));
        helper.setBlock(posC, display(Direction.NORTH));

        // None of the blocks should have a 2×2 layout
        helper.assertBlockProperty(posA, TextDisplayBlock.LAYOUT, DisplayLayout.RIGHT_2x1);
        helper.assertBlockProperty(posB, TextDisplayBlock.LAYOUT, DisplayLayout.LEFT_2x1);
        helper.assertBlockProperty(posC, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Different facing — should not merge
    // -------------------------------------------------------------------------

    /**
     * Two adjacent blocks with different FACING directions must not merge.
     *   A at (3,1,3) FACING=NORTH
     *   B at (4,1,3) FACING=EAST
     * Both should stay SINGLE_1x1.
     */
    @GameTest(template = TEMPLATE)
    public static void differentFacing_doesNotMerge(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3);
        BlockPos posB = new BlockPos(4, 1, 3);
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.EAST));

        helper.assertBlockProperty(posA, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.assertBlockProperty(posB, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // 2×3 grid — two stacked 2×1 rows joined by a middle row
    // -------------------------------------------------------------------------

    /**
     * Build a 2×3 grid by:
     *   1. Placing a 2×1 at y=1  (A, B)
     *   2. Placing another 2×1 at y=3  (C, D) — one block of air between them
     *   3. Filling the gap at y=2  (E, F)
     *
     * Grid layout (FACING=NORTH, left=east):
     *   C  D   y=3  (top)
     *   E  F   y=2  (middle)
     *   A  B   y=1  (bottom)
     *
     * The algorithm cannot form a single 2×3 display, so it must split into a
     * 2×2 + 2×1.  Either split is acceptable:
     *   • Config 1 — bottom 2×2 {A,B,E,F} + top 2×1 {C,D}
     *   • Config 2 — top 2×2 {C,D,E,F}    + bottom 2×1 {A,B}
     *
     * The test fails if neither valid configuration is present (e.g. any block
     * ends up SINGLE_1x1, or the two 2×2 blocks disagree on membership).
     */
    @GameTest(template = TEMPLATE)
    public static void twoByThreeGrid_isConsistent(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3); // bottom-right
        BlockPos posB = new BlockPos(4, 1, 3); // bottom-left  (posA.east())
        BlockPos posE = new BlockPos(3, 2, 3); // middle-right (posA.above())
        BlockPos posF = new BlockPos(4, 2, 3); // middle-left  (posB.above())
        BlockPos posC = new BlockPos(3, 3, 3); // top-right    (posE.above())
        BlockPos posD = new BlockPos(4, 3, 3); // top-left     (posF.above())

        // Place bottom 2×1, then top 2×1, then fill the gap
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));
        helper.setBlock(posC, display(Direction.NORTH));
        helper.setBlock(posD, display(Direction.NORTH));
        helper.setBlock(posE, display(Direction.NORTH));
        helper.setBlock(posF, display(Direction.NORTH));

        DisplayLayout layoutA = helper.getBlockState(posA).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutB = helper.getBlockState(posB).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutE = helper.getBlockState(posE).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutF = helper.getBlockState(posF).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutC = helper.getBlockState(posC).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutD = helper.getBlockState(posD).getValue(TextDisplayBlock.LAYOUT);

        // Config 1: bottom 2×2 (A,B,E,F) + top 2×1 (C,D)
        boolean config1 = layoutA == DisplayLayout.BOTTOM_RIGHT_2x2
                       && layoutB == DisplayLayout.BOTTOM_LEFT_2x2
                       && layoutE == DisplayLayout.TOP_RIGHT_2x2
                       && layoutF == DisplayLayout.TOP_LEFT_2x2
                       && layoutC == DisplayLayout.RIGHT_2x1
                       && layoutD == DisplayLayout.LEFT_2x1;

        // Config 2: top 2×2 (C,D,E,F) + bottom 2×1 (A,B)
        boolean config2 = layoutA == DisplayLayout.RIGHT_2x1
                       && layoutB == DisplayLayout.LEFT_2x1
                       && layoutE == DisplayLayout.BOTTOM_RIGHT_2x2
                       && layoutF == DisplayLayout.BOTTOM_LEFT_2x2
                       && layoutC == DisplayLayout.TOP_RIGHT_2x2
                       && layoutD == DisplayLayout.TOP_LEFT_2x2;

        if (!config1 && !config2) {
            helper.fail(String.format(
                "2x3 grid ended up in an invalid state. Layouts: A=%s B=%s E=%s F=%s C=%s D=%s",
                layoutA, layoutB, layoutE, layoutF, layoutC, layoutD));
            return;
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // 3×2 grid — two stacked 1×2 pillars joined by a middle column
    // -------------------------------------------------------------------------

    /**
     * Build a 3×2 grid by:
     *   1. Placing a 1×2 pillar at x=3  (A bottom, B top)
     *   2. Placing another 1×2 pillar at x=5  (C bottom, D top) — one column gap
     *   3. Filling the gap at x=4  (E bottom, F top)
     *
     * Grid layout (FACING=NORTH, left=east/+X, viewed from front):
     *   B  F  D   y=2  (top)
     *   A  E  C   y=1  (bottom)
     *
     * The algorithm cannot form a single 3×2 display, so it must split into a
     * 2×2 + 1×2.  Either split is acceptable:
     *   • Config 1 — left  2×2 {A,B,E,F} + right 1×2 {C,D}
     *   • Config 2 — right 2×2 {E,F,C,D} + left  1×2 {A,B}
     *
     * The test fails if neither valid configuration is present.
     */
    @GameTest(template = TEMPLATE)
    public static void threeByTwoGrid_isConsistent(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3); // left-bottom
        BlockPos posB = new BlockPos(3, 2, 3); // left-top    (posA.above())
        BlockPos posC = new BlockPos(5, 1, 3); // right-bottom
        BlockPos posD = new BlockPos(5, 2, 3); // right-top   (posC.above())
        BlockPos posE = new BlockPos(4, 1, 3); // mid-bottom  (posA.east())
        BlockPos posF = new BlockPos(4, 2, 3); // mid-top     (posB.east())

        // Place left 1×2, then right 1×2, then fill the gap
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));
        helper.setBlock(posC, display(Direction.NORTH));
        helper.setBlock(posD, display(Direction.NORTH));
        helper.setBlock(posE, display(Direction.NORTH));
        helper.setBlock(posF, display(Direction.NORTH));

        DisplayLayout layoutA = helper.getBlockState(posA).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutB = helper.getBlockState(posB).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutC = helper.getBlockState(posC).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutD = helper.getBlockState(posD).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutE = helper.getBlockState(posE).getValue(TextDisplayBlock.LAYOUT);
        DisplayLayout layoutF = helper.getBlockState(posF).getValue(TextDisplayBlock.LAYOUT);

        // Config 1: left 2×2 {A,B,E,F} + right 1×2 {C,D}
        boolean config1 = layoutA == DisplayLayout.BOTTOM_RIGHT_2x2
                       && layoutB == DisplayLayout.TOP_RIGHT_2x2
                       && layoutE == DisplayLayout.BOTTOM_LEFT_2x2
                       && layoutF == DisplayLayout.TOP_LEFT_2x2
                       && layoutC == DisplayLayout.BOTTOM_1x2
                       && layoutD == DisplayLayout.TOP_1x2;

        // Config 2: right 2×2 {E,F,C,D} + left 1×2 {A,B}
        boolean config2 = layoutA == DisplayLayout.BOTTOM_1x2
                       && layoutB == DisplayLayout.TOP_1x2
                       && layoutE == DisplayLayout.BOTTOM_RIGHT_2x2
                       && layoutF == DisplayLayout.TOP_RIGHT_2x2
                       && layoutC == DisplayLayout.BOTTOM_LEFT_2x2
                       && layoutD == DisplayLayout.TOP_LEFT_2x2;

        if (!config1 && !config2) {
            helper.fail(String.format(
                "3x2 grid ended up in an invalid state. Layouts: A=%s B=%s E=%s F=%s C=%s D=%s",
                layoutA, layoutB, layoutE, layoutF, layoutC, layoutD));
            return;
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // 1×2 pillar — side neighbours must not break the merge
    // -------------------------------------------------------------------------

    /**
     * A 1×2 pillar with a block placed beside each side should keep its 1×2
     * layout; the new neighbours have no valid merge partner and stay SINGLE_1x1.
     *
     *   Structure (FACING=NORTH, left=east):
     *     B at (3,2,3)  ← top of pillar
     *     A at (3,1,3)  ← bottom of pillar
     *     C at (4,1,3)  ← left  neighbour (east of A)
     *     D at (2,1,3)  ← right neighbour (west of A)
     *
     *   Expected:
     *     A → BOTTOM_1x2, B → TOP_1x2, C → SINGLE_1x1, D → SINGLE_1x1
     */
    @GameTest(template = TEMPLATE)
    public static void pillar1x2_sideNeighboursDoNotBreak(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 1, 3);
        BlockPos posB = new BlockPos(3, 2, 3); // posA.above()
        BlockPos posC = new BlockPos(4, 1, 3); // posA.east()  — left  side
        BlockPos posD = new BlockPos(2, 1, 3); // posA.west()  — right side
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));
        helper.setBlock(posC, display(Direction.NORTH));
        helper.setBlock(posD, display(Direction.NORTH));

        helper.assertBlockProperty(posA, TextDisplayBlock.LAYOUT, DisplayLayout.BOTTOM_1x2);
        helper.assertBlockProperty(posB, TextDisplayBlock.LAYOUT, DisplayLayout.TOP_1x2);
        helper.assertBlockProperty(posC, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.assertBlockProperty(posD, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // 2×1 row — vertical neighbours must not break the merge
    // -------------------------------------------------------------------------

    /**
     * A 2×1 row with a block placed above and below should keep its 2×1
     * layout; the new neighbours have no valid merge partner and stay SINGLE_1x1.
     *
     * The row is placed at y=2 so there is headroom at y=3 (above) and y=1
     * (below) inside the template.
     *
     *   Structure (FACING=NORTH, left=east):
     *     A at (3,2,3)  B at (4,2,3)  ← the 2×1 row
     *     C at (3,3,3)                 ← above A
     *     D at (3,1,3)                 ← below A
     *
     *   Expected:
     *     A → RIGHT_2x1, B → LEFT_2x1, C → SINGLE_1x1, D → SINGLE_1x1
     */
    @GameTest(template = TEMPLATE)
    public static void row2x1_verticalNeighboursDoNotBreak(GameTestHelper helper) {
        BlockPos posA = new BlockPos(3, 2, 3);
        BlockPos posB = new BlockPos(4, 2, 3); // posA.east()
        BlockPos posC = new BlockPos(3, 3, 3); // posA.above()
        BlockPos posD = new BlockPos(3, 1, 3); // posA.below()
        helper.setBlock(posA, display(Direction.NORTH));
        helper.setBlock(posB, display(Direction.NORTH));
        helper.setBlock(posC, display(Direction.NORTH));
        helper.setBlock(posD, display(Direction.NORTH));

        helper.assertBlockProperty(posA, TextDisplayBlock.LAYOUT, DisplayLayout.RIGHT_2x1);
        helper.assertBlockProperty(posB, TextDisplayBlock.LAYOUT, DisplayLayout.LEFT_2x1);
        helper.assertBlockProperty(posC, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.assertBlockProperty(posD, TextDisplayBlock.LAYOUT, DisplayLayout.SINGLE_1x1);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Dye
    // -------------------------------------------------------------------------

    private static ItemInteractionResult useOn(GameTestHelper helper, BlockPos pos, ItemStack stack, Player player) {
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = helper.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        return state.useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
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

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack dye = new ItemStack(Items.RED_DYE, 2);
        ItemInteractionResult result = useOn(helper, pos, dye, player);

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

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        helper.assertBlockProperty(a, TextDisplayBlock.LAYOUT, DisplayLayout.TOP_RIGHT_2x2);

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack dye = new ItemStack(Items.LIME_DYE, 1);
        useOn(helper, d, dye, player);

        for (BlockPos p : new BlockPos[]{a, b, c, d}) {
            helper.assertTrue(colorAt(helper, p) == DyeColor.LIME, "Merged display block at " + p + " should be lime");
        }
        helper.assertTrue(colorAt(helper, lone) == DyeColor.WHITE, "Unrelated display should stay white");
        helper.assertTrue(dye.getCount() == 1, "Creative players should not consume the dye");
        helper.succeed();
    }
}
