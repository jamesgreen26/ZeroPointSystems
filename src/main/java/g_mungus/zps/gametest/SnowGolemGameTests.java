package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Covers the snow golem half of {@code PowderSnowCauldrons}: a golem over a cauldron fills it with powder snow. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SnowGolemGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos CAULDRON = new BlockPos(3, 2, 3);

    /** Centred over the basin, which is wide enough for a golem to drop into. */
    private static final Vec3 IN_BASIN = new Vec3(3.5, 3.0, 3.5);

    /** Overlapping the west wall, so the golem lands on the rim instead. */
    private static final Vec3 ON_RIM = new Vec3(3.05, 3.0, 3.5);

    /** Time to fall one block, settle, and lay three layers at one per tick. */
    private static final int SETTLE_TICKS = 20;

    @GameTest(template = TEMPLATE)
    public static void golemInBasin_fillsEmptyCauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.CAULDRON);
        spawnStillGolem(helper, IN_BASIN);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertPowderSnowLevel(helper, 3))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void golemOnRim_topsUpPowderSnowCauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON, powderSnowCauldron(1));
        spawnStillGolem(helper, ON_RIM);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertPowderSnowLevel(helper, 3))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void golem_leavesWaterCauldronAlone(GameTestHelper helper) {
        BlockState water = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1);
        helper.setBlock(CAULDRON, water);
        spawnStillGolem(helper, IN_BASIN);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> helper.assertBlockState(CAULDRON, water::equals, () -> "Water cauldron should be untouched"))
                .thenSucceed();
    }

    /** No AI so the golem stays put; it still ticks its snow trail. */
    private static void spawnStillGolem(GameTestHelper helper, Vec3 pos) {
        SnowGolem golem = helper.spawn(EntityType.SNOW_GOLEM, pos);
        golem.setNoAi(true);
    }

    private static BlockState powderSnowCauldron(int level) {
        return Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level);
    }

    private static void assertPowderSnowLevel(GameTestHelper helper, int level) {
        helper.assertBlockPresent(Blocks.POWDER_SNOW_CAULDRON, CAULDRON);
        helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, level);
    }
}
