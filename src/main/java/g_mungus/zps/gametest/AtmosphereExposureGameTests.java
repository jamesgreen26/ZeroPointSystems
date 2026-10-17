package g_mungus.zps.gametest;

import com.mojang.authlib.GameProfile;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.gas.AtmosphereBreather;
import g_mungus.zps.gas.AtmosphereExposure;
import g_mungus.zps.gas.ModGasTags;
import g_mungus.zps.gas.api.GasConditions;
import g_mungus.zps.world.AtmosphericComposition;
import g_mungus.zps.world.BiomeTemperature;
import g_mungus.zps.world.YRange;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Covers {@link AtmosphereExposure}: the overworld atmosphere's air and vacuum, an unregistered gas, and a mob left in mild air. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class AtmosphereExposureGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void overworldAtmosphere_airAndVacuum_bothExposed(GameTestHelper helper) {
        BlockPos column = helper.absolutePos(BlockPos.ZERO);
        expectBiomeTemperature(helper, column.atY(1000), "Air at y=1000");
        expectBiomeTemperature(helper, column.atY(2100), "Vacuum at y=2100");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unregisteredGas_isNotExposable(GameTestHelper helper) {
        AtmosphericComposition unknown = new AtmosphericComposition(
                Map.of(YRange.ALL, Map.of(ZPSMod.resource("no_such_gas"), 1.0)));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        if (!Double.isNaN(AtmosphereExposure.temperatureK(helper.getLevel().getBiome(pos).value(), unknown, pos))) {
            helper.fail("A composition of only unregistered gas should expose nothing");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void air_isBreathableByTag(GameTestHelper helper) {
        var gases = helper.getLevel().registryAccess().registryOrThrow(GasTypeRegistry.INSTANCE.getGAS_TYPE_REGISTRY_KEY());
        if (!gases.getHolderOrThrow(ResourceKey.create(gases.key(), AtmosphericComposition.AIR)).is(ModGasTags.BREATHABLE)) {
            helper.fail("kelvin:air should be in #zps:breathable");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mobInVacuum_drowns(GameTestHelper helper) {
        Pig pig = floatingPig(helper, 2100);
        helper.runAfterDelay(40, () -> {
            if (pig.getAirSupply() >= pig.getMaxAirSupply()) helper.fail("A pig in vacuum should be losing air, has " + pig.getAirSupply());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void mobInHighAir_breathes(GameTestHelper helper) {
        Pig pig = floatingPig(helper, 1000);
        helper.runAfterDelay(40, () -> {
            if (pig.getAirSupply() < pig.getMaxAirSupply()) helper.fail("A pig in air should keep its breath, has " + pig.getAirSupply());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void waterBreathingMobInVacuum_breathes(GameTestHelper helper) {
        Pig pig = floatingPig(helper, 2100);
        pig.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200));
        helper.runAfterDelay(40, () -> {
            if (pig.getAirSupply() < pig.getMaxAirSupply()) helper.fail("Water breathing should hold off drowning in vacuum too, has " + pig.getAirSupply());
            helper.succeed();
        });
    }

    /**
     * The server tells a player's client when its drowning starts and stops. A fake player has no
     * client and is never ticked, so its base tick is driven by hand and what is checked is the
     * record of what was sent.
     */
    @GameTest(template = TEMPLATE)
    public static void playerDrowning_isSyncedOnChange(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("zps:drowning_sync".getBytes()), "[ZPS drowning sync]"));
        BlockPos column = helper.absolutePos(new BlockPos(3, 2, 3));
        AtmosphereBreather breather = (AtmosphereBreather) player;

        player.moveTo(column.getX() + 0.5, 2100, column.getZ() + 0.5);
        player.baseTick();
        if (!breather.zps$isSyncedDrowning()) helper.fail("A player in vacuum should have been told it is drowning");

        // Past the tick of grace a drowning mark gets, so the vacuum's mark has lapsed.
        helper.runAfterDelay(2, () -> {
            player.moveTo(column.getX() + 0.5, 1000, column.getZ() + 0.5);
            player.baseTick();
            if (breather.zps$isSyncedDrowning()) helper.fail("A player back in air should have been told it is breathing");
            helper.succeed();
        });
    }

    /** The server keeps what it applied to a player, for the debug screen to ask for. */
    @GameTest(template = TEMPLATE)
    public static void playerConditions_areKept(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("zps:gas_conditions".getBytes()), "[ZPS gas conditions]"));
        BlockPos column = helper.absolutePos(new BlockPos(3, 2, 3));
        player.moveTo(column.getX() + 0.5, 2100, column.getZ() + 0.5);
        player.baseTick();

        GasConditions conditions = ((AtmosphereBreather) player).zps$getGasConditions();
        if (conditions == null) helper.fail("The player's gas conditions should have been kept");
        else if (conditions.breathable() || !conditions.gases().equals(List.of(AtmosphericComposition.VACUUM)) || !conditions.exposed()) {
            helper.fail("A player in vacuum should be exposed to unbreathable zps:vacuum, was " + conditions);
        }
        helper.succeed();
    }

    /** A pig hung in the sky over the test, at absolute height {@code y}, ticking but not falling. */
    private static Pig floatingPig(GameTestHelper helper, int y) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 3));
        BlockPos column = helper.absolutePos(new BlockPos(3, 2, 3));
        pig.setNoGravity(true);
        pig.moveTo(column.getX() + 0.5, y, column.getZ() + 0.5);
        return pig;
    }

    private static void expectBiomeTemperature(GameTestHelper helper, BlockPos pos, String what) {
        double expected = BiomeTemperature.kelvinAt(helper.getLevel().getBiome(pos).value(), pos);
        double actual = AtmosphereExposure.temperatureK(helper.getLevel(), pos);
        if (actual != expected) helper.fail(what + " should be " + expected + " K, was " + actual);
    }

    @GameTest(template = TEMPLATE)
    public static void mobInMildAir_isLeftAlone(GameTestHelper helper) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 3));
        helper.runAfterDelay(40, () -> {
            if (pig.getTicksFrozen() > 0 || pig.isOnFire()) helper.fail("A pig in ordinary air should neither freeze nor burn");
            helper.succeed();
        });
    }
}
