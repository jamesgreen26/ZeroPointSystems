package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.GasExposure;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * What gas does to something standing in it, checked on a pig: unlike the mock player the duct
 * tests ride, a pig really ticks, so what vanilla does with the fire and the frost afterwards —
 * the damage, above all — is part of what is tested.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GasExposureGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos STAND = new BlockPos(3, 2, 3);

    /** A pig exposed to gas at {@code temperature} kelvin every tick until the test ends. */
    private static Pig pigIn(GameTestHelper helper, double temperature) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, STAND);
        helper.onEachTick(() -> GasExposure.expose(pig, temperature));
        return pig;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void hotGasBurns(GameTestHelper helper) {
        Pig pig = pigIn(helper, ZPSConfig.entityBurnGasTemperatureK());

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(pig.isOnFire(), "The pig should be on fire");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "The pig should have been hurt");
            helper.succeed();
        });
    }

    /**
     * Frost has to go all the way before it hurts, which takes as long as it does in powder snow,
     * and then vanilla deals the damage every couple of seconds. This waits for the first of them.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void coldGasFreezesAndThenHurts(GameTestHelper helper) {
        Pig pig = pigIn(helper, ZPSConfig.entityFreezeGasTemperatureK());

        helper.runAfterDelay(20, () -> helper.assertTrue(pig.getTicksFrozen() > 0, "The pig should be freezing"));
        helper.runAfterDelay(pig.getTicksRequiredToFreeze() + 60, () -> {
            helper.assertTrue(pig.isFullyFrozen(), "The pig should be fully frozen by now");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "A fully frozen pig should be taking damage");
            helper.assertFalse(pig.isOnFire(), "A freezing pig should not be on fire");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void mildGasDoesNothing(GameTestHelper helper) {
        double mild = (ZPSConfig.entityBurnGasTemperatureK() + ZPSConfig.entityFreezeGasTemperatureK()) / 2.0;
        Pig pig = pigIn(helper, mild);

        helper.runAfterDelay(60, () -> {
            helper.assertFalse(pig.isOnFire(), "The pig should not be on fire");
            helper.assertTrue(pig.getTicksFrozen() == 0, "The pig should not be freezing");
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "The pig should be unhurt");
            helper.succeed();
        });
    }

    /** No gas at all, which is what a vent reports once nothing has passed it for a while. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void noGasDoesNothing(GameTestHelper helper) {
        Pig pig = pigIn(helper, Double.NaN);

        helper.runAfterDelay(60, () -> {
            helper.assertFalse(pig.isOnFire(), "The pig should not be on fire");
            helper.assertTrue(pig.getTicksFrozen() == 0, "The pig should not be freezing");
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "The pig should be unhurt");
            helper.succeed();
        });
    }
}
