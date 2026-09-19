package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Covers {@code PowderSnowDripstone} and its mixins: a stalactite whose support block has powder
 * snow on top drips into the cauldron below one layer at a time, and the vanilla water drip is
 * left as it was.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class DripstoneGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";

    /** A single-block stalactite hanging from a stone block, with the source on top of that stone. */
    private static final BlockPos CAULDRON = new BlockPos(5, 2, 5);
    private static final BlockPos TIP = new BlockPos(5, 4, 5);
    private static final BlockPos SUPPORT = new BlockPos(5, 5, 5);
    private static final BlockPos SOURCE = new BlockPos(5, 6, 5);

    /**
     * The drip lands 50 ticks plus one per block of fall (TIP to CAULDRON is 2) after it leaves
     * the tip; the slack is for the tick that schedules it. A literal so it can size a timeout.
     */
    private static final int DRIP_TICKS = 50 + 2 + 5;

    /** Every random tick's roll is below the fill chance, so the tip always drips. */
    private static final float ALWAYS_DRIP = 0.0F;

    @GameTest(template = TEMPLATE)
    public static void powderSnow_startsEmptyCauldron(GameTestHelper helper) {
        buildStalactite(helper, Blocks.POWDER_SNOW.defaultBlockState());
        helper.setBlock(CAULDRON, Blocks.CAULDRON);

        helper.startSequence()
                .thenExecute(() -> drip(helper))
                .thenExecute(() -> helper.assertBlockPresent(Blocks.CAULDRON, CAULDRON))
                .thenIdle(DRIP_TICKS)
                .thenExecute(() -> assertPowderSnowLevel(helper, 1))
                .thenSucceed();
    }

    /** Three drips in sequence outrun the default 100-tick test timeout. */
    @GameTest(template = TEMPLATE, timeoutTicks = 3 * DRIP_TICKS + 20)
    public static void powderSnow_fillsLayerByLayerUntilFull(GameTestHelper helper) {
        buildStalactite(helper, Blocks.POWDER_SNOW.defaultBlockState());
        helper.setBlock(CAULDRON, powderSnowCauldron(1));

        helper.startSequence()
                .thenExecute(() -> drip(helper))
                .thenIdle(DRIP_TICKS)
                .thenExecute(() -> assertPowderSnowLevel(helper, 2))
                .thenExecute(() -> drip(helper))
                .thenIdle(DRIP_TICKS)
                .thenExecute(() -> assertPowderSnowLevel(helper, 3))
                .thenExecute(() -> drip(helper))
                .thenIdle(DRIP_TICKS)
                .thenExecute(() -> assertPowderSnowLevel(helper, 3))
                .thenSucceed();
    }

    /** Powder snow must not top up a water cauldron, just as water does not top up a snow one. */
    @GameTest(template = TEMPLATE)
    public static void powderSnow_leavesWaterCauldronAlone(GameTestHelper helper) {
        buildStalactite(helper, Blocks.POWDER_SNOW.defaultBlockState());
        BlockState water = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1);
        helper.setBlock(CAULDRON, water);

        helper.startSequence()
                .thenExecute(() -> drip(helper))
                .thenIdle(DRIP_TICKS)
                .thenExecute(() -> helper.assertBlockState(CAULDRON, water::equals, () -> "Water cauldron should be untouched"))
                .thenSucceed();
    }

    /** The vanilla path still runs when the source is water. */
    @GameTest(template = TEMPLATE)
    public static void water_stillFillsWaterCauldron(GameTestHelper helper) {
        buildStalactite(helper, Blocks.WATER.defaultBlockState());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(SOURCE.relative(side), Blocks.STONE);
        }
        helper.setBlock(CAULDRON, Blocks.CAULDRON);

        helper.startSequence()
                .thenExecute(() -> drip(helper))
                .thenIdle(DRIP_TICKS)
                .thenExecute(() -> helper.assertBlockPresent(Blocks.WATER_CAULDRON, CAULDRON))
                .thenExecute(() -> helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, 1))
                .thenSucceed();
    }

    private static void buildStalactite(GameTestHelper helper, BlockState source) {
        helper.setBlock(SUPPORT, Blocks.STONE);
        helper.setBlock(TIP, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
        helper.setBlock(SOURCE, source);
    }

    /** Runs the random-tick fill logic once with a roll that always drips. */
    private static void drip(GameTestHelper helper) {
        PointedDripstoneBlock.maybeTransferFluid(
                helper.getBlockState(TIP), helper.getLevel(), helper.absolutePos(TIP), ALWAYS_DRIP);
    }

    private static BlockState powderSnowCauldron(int level) {
        return Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
    }

    private static void assertPowderSnowLevel(GameTestHelper helper, int level) {
        helper.assertBlockPresent(Blocks.POWDER_SNOW_CAULDRON, CAULDRON);
        helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, level);
    }
}
