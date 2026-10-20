package g_mungus.zps.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.LaunchPlatformLayout;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;

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
        helper.assertValueEqual(layout.min(), helper.absolutePos(min), "rectangle min");
        helper.assertValueEqual(layout.max(), helper.absolutePos(max), "rectangle max");
        helper.assertValueEqual(layout.height(), height, "height");
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

    /** Powering a platform lifts everything above it up to the tower's top, and leaves the platform and tower. */
    @GameTest(template = TEMPLATE, batch = "launchPlatformSable", timeoutTicks = 40)
    public static void power_assemblesVolume(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        platforms(helper, 3, 3, 5, 5);
        tower(helper, 6, 4, 2);
        BlockPos lifted = new BlockPos(4, 2, 4);
        BlockPos liftedTop = new BlockPos(4, 3, 4);
        BlockPos aboveTower = new BlockPos(4, 4, 4);
        helper.setBlock(lifted, Blocks.STONE);
        helper.setBlock(liftedTop, Blocks.STONE);
        helper.setBlock(aboveTower, Blocks.GOLD_BLOCK);

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        List<SubLevel> before = new ArrayList<>(container.getAllSubLevels());

        helper.setBlock(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            List<SubLevel> created = new ArrayList<>(container.getAllSubLevels());
            created.removeAll(before);
            try {
                helper.assertBlockPresent(Blocks.AIR, lifted);
                helper.assertBlockPresent(Blocks.AIR, liftedTop);
                helper.assertBlockPresent(Blocks.GOLD_BLOCK, aboveTower);
                helper.assertBlockPresent(ModBlocks.LAUNCH_PLATFORM.get(), new BlockPos(4, 1, 4));
                helper.assertBlockPresent(ModBlocks.SPACE_TRUSS.get(), new BlockPos(6, 3, 4));
                helper.assertValueEqual(created.size(), 1, "sublevels created");
            } finally {
                for (SubLevel subLevel : created) {
                    container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                }
            }
            helper.succeed();
        });
    }

    /** A platform carried on a sublevel launches a sublevel of its own off it. */
    @GameTest(template = TEMPLATE, batch = "launchPlatformSable_onSubLevel", timeoutTicks = 40)
    public static void power_onSubLevelAssemblesVolume(GameTestHelper helper) {
        if (!Compat.isSableLoaded()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        platforms(helper, 3, 3, 5, 5);
        tower(helper, 6, 4, 2);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.IRON_BLOCK);
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.IRON_BLOCK);
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(6, 3, 5)))) {
            // The template's floor shares the platform layer.
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.LAUNCH_PLATFORM.get()) || state.is(ModBlocks.SPACE_TRUSS.get()) || state.is(Blocks.IRON_BLOCK)) {
                blocks.add(pos.immutable());
            }
        }
        SableBeamRig rig = SableBeamRig.assemble(level, helper.absolutePos(new BlockPos(3, 1, 3)),
                helper.absolutePos(new BlockPos(6, 3, 5)), blocks, new Quaterniond());

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        List<SubLevel> before = new ArrayList<>(container.getAllSubLevels());
        BlockPos platform = null;
        List<BlockPos> cargo = new ArrayList<>();
        for (BlockPos pos : rig.blocksLeft()) {
            if (level.getBlockState(pos).is(ModBlocks.LAUNCH_PLATFORM.get()) && platform == null) platform = pos;
            if (level.getBlockState(pos).is(Blocks.IRON_BLOCK)) cargo.add(pos);
        }
        helper.assertTrue(platform != null && cargo.size() == 2, "The rig should carry the platform and both iron blocks");
        // Beneath the platform is outside the rig, and empty.
        level.setBlockAndUpdate(platform.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            List<SubLevel> created = new ArrayList<>(container.getAllSubLevels());
            created.removeAll(before);
            try {
                for (BlockPos pos : cargo) {
                    helper.assertTrue(level.getBlockState(pos).isAir(), "Cargo at " + pos + " should have launched");
                }
                helper.assertValueEqual(created.size(), 1, "sublevels created");
            } finally {
                for (SubLevel subLevel : created) {
                    container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                }
                rig.remove();
            }
            helper.succeed();
        });
    }

    /** Without a tower nothing is lifted. */
    @GameTest(template = TEMPLATE, batch = "launchPlatformSable", timeoutTicks = 40)
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
