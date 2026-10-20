package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.LaunchPlatformBlock;
import g_mungus.zps.block.LaunchPlatformLayout;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LaunchPlatformGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";

    private static void platforms(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ModBlocks.LAUNCH_PLATFORM.get());
            }
        }
    }

    /** A truss on the platform layer at (x, z) with {@code above} more stacked on it. */
    private static void tower(GameTestHelper helper, int x, int z, int above) {
        for (int y = 1; y <= 1 + above; y++) {
            helper.setBlock(new BlockPos(x, y, z), ModBlocks.SPACE_TRUSS.get());
        }
    }

    private static void assertLayout(GameTestHelper helper, LaunchPlatformLayout layout,
                                     BlockPos min, BlockPos max, int height) {
        helper.assertTrue(layout != null, "Expected a layout, found none");
        assertEqual(helper, layout.min(), helper.absolutePos(min), "rectangle min");
        assertEqual(helper, layout.max(), helper.absolutePos(max), "rectangle max");
        assertEqual(helper, layout.height(), height, "height");
    }

    /** 1.20.1's GameTestHelper has no assertValueEqual. */
    private static void assertEqual(GameTestHelper helper, Object actual, Object expected, String name) {
        helper.assertTrue(Objects.equals(actual, expected), "Expected " + name + " to be " + expected + ", is " + actual);
    }

    /** The largest rectangle wins, and it is as tall as the tallest tower beside it. */
    @GameTest(template = TEMPLATE)
    public static void layout_largestRectangleWithTallestTower(GameTestHelper helper) {
        platforms(helper, 2, 2, 6, 4);
        // A narrow tongue off the side, which a larger rectangle cannot include.
        platforms(helper, 4, 5, 4, 8);
        tower(helper, 7, 3, 2);
        tower(helper, 3, 5, 4);

        LaunchPlatformLayout layout = LaunchPlatformLayout.find(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 3)));
        assertLayout(helper, layout, new BlockPos(2, 1, 2), new BlockPos(6, 1, 4), 4);
        helper.assertFalse(layout.tooBig(), "A 5x4x3 volume is not too big");
        helper.succeed();
    }

    /** A rectangle with no tower beside it is passed over for a smaller one that has one. */
    @GameTest(template = TEMPLATE)
    public static void layout_skipsRectanglesWithoutTower(GameTestHelper helper) {
        platforms(helper, 2, 2, 6, 4);
        platforms(helper, 4, 5, 4, 8);
        tower(helper, 5, 8, 3);

        LaunchPlatformLayout layout = LaunchPlatformLayout.find(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 3)));
        assertLayout(helper, layout, new BlockPos(4, 1, 2), new BlockPos(4, 1, 8), 3);
        helper.succeed();
    }

    /** A tower stops at the first truss laid sideways, and one laid sideways at the base is no tower. */
    @GameTest(template = TEMPLATE)
    public static void layout_sidewaysTrussesDoNotCount(GameTestHelper helper) {
        platforms(helper, 2, 2, 4, 4);
        tower(helper, 5, 3, 4);
        helper.setBlock(new BlockPos(5, 4, 3), ModBlocks.SPACE_TRUSS.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        tower(helper, 1, 3, 5);
        helper.setBlock(new BlockPos(1, 1, 3), ModBlocks.SPACE_TRUSS.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));

        LaunchPlatformLayout layout = LaunchPlatformLayout.find(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)));
        assertLayout(helper, layout, new BlockPos(2, 1, 2), new BlockPos(4, 1, 4), 2);
        helper.succeed();
    }

    /** Towers at a corner, or with nothing stacked on them, do not count. */
    @GameTest(template = TEMPLATE)
    public static void layout_noneWithoutSideTower(GameTestHelper helper) {
        platforms(helper, 2, 2, 4, 4);
        tower(helper, 5, 5, 3);
        tower(helper, 5, 3, 0);

        helper.assertTrue(LaunchPlatformLayout.find(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))) == null,
                "Expected no layout");
        helper.succeed();
    }

    /** Power from above, such as from something built on the platform, does not launch it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void power_fromAboveDoesNothing(GameTestHelper helper) {
        platforms(helper, 3, 3, 5, 5);
        tower(helper, 6, 4, 2);
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(Blocks.REDSTONE_BLOCK, new BlockPos(4, 2, 4));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 4));
            helper.assertBlockProperty(new BlockPos(4, 1, 4), LaunchPlatformBlock.POWERED, false);
            helper.succeed();
        });
    }

    /** Without a tower nothing is lifted. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void power_withoutTowerDoesNothing(GameTestHelper helper) {
        platforms(helper, 3, 3, 5, 5);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 2, 4));
            helper.succeed();
        });
    }
}
