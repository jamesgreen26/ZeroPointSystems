package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.gas.ModGases;
import g_mungus.zps.gas.api.GasEnvironmentEvent;
import g_mungus.zps.gas.api.GasEnvironments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.function.Consumer;

/** Covers the gas environment API: queued modifiers, event listeners, and how a stale queue lapses. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GasEnvironmentGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos STAND = new BlockPos(3, 2, 3);

    /** Replacing the gases replaces what is breathed: flux in place of the ground's air drowns. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void queuedGases_replaceTheAtmosphere(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, STAND);
        helper.onEachTick(() -> GasEnvironments.queue(pig,
                environment -> environment.setGases(Map.of(ModGases.FLUX.getResourceLocation(), 1.0))));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(pig.getAirSupply() < pig.getMaxAirSupply(), "The pig should be losing air, has " + pig.getAirSupply());
            helper.assertFalse(pig.isOnFire(), "Only the gases were replaced, so the pig should not be on fire");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void queuedTemperature_replacesTheAtmosphere(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, STAND);
        double hot = ZPSConfig.entityBurnGasTemperatureK() * 2.0;
        helper.onEachTick(() -> GasEnvironments.queue(pig, environment -> environment.setTemperatureK(hot)));

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(pig.isOnFire(), "The pig should be on fire");
            helper.assertTrue(pig.getAirSupply() == pig.getMaxAirSupply(), "Only the temperature was replaced, so the pig should breathe");
            helper.succeed();
        });
    }

    /** A listener that shields the entity stops both the vacuum's cold and its drowning. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void listener_canShieldFromVacuum(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, STAND);
        BlockPos column = helper.absolutePos(STAND);
        pig.setNoGravity(true);
        pig.moveTo(column.getX() + 0.5, 2100, column.getZ() + 0.5);
        Consumer<GasEnvironmentEvent> shield = event -> {
            if (event.getEntity() == pig) {
                event.getEnvironment().setExposed(false);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(shield);

        helper.runAfterDelay(40, () -> {
            MinecraftForge.EVENT_BUS.unregister(shield);
            helper.assertTrue(pig.getAirSupply() == pig.getMaxAirSupply(), "A shielded pig should keep its breath, has " + pig.getAirSupply());
            helper.assertTrue(pig.getTicksFrozen() == 0, "A shielded pig should not freeze");
            helper.succeed();
        });
    }

    /** A modifier holds for the entity's next tick only; one queued once is soon gone. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void queuedModifier_lapsesWhenNotRenewed(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, STAND);
        GasEnvironments.queue(pig, environment -> environment.setGases(Map.of()));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(pig.getAirSupply() == pig.getMaxAirSupply(),
                    "One tick without air should long since have been made up, has " + pig.getAirSupply());
            helper.succeed();
        });
    }
}
