package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.mixin.BiomeInvoker;
import g_mungus.zps.world.AtmosphereTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Covers {@code SkyBiomeSettings} in the overworld, with and without a loaded chunk, and the atmosphere biome's temperature curve. */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SkyBiomeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final ResourceKey<Biome> ATMOSPHERE = ResourceKey.create(Registries.BIOME, ZPSMod.resource("overworld_atmosphere"));

    @GameTest(template = TEMPLATE)
    public static void overworldAboveBuildLimit_isAtmosphere(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkAccess chunk = helper.getLevel().getChunk(origin);
        int top = helper.getLevel().getMaxBuildHeight();

        if (!biomeAt(chunk, origin, top).is(ATMOSPHERE)) helper.fail("y=" + top + " should be the atmosphere");
        if (!biomeAt(chunk, origin, 5000).is(ATMOSPHERE)) helper.fail("y=5000 should be the atmosphere");
        if (biomeAt(chunk, origin, top - 1).is(ATMOSPHERE)) helper.fail("y=" + (top - 1) + " is inside the world and keeps its biome");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unloadedColumnAboveBuildLimit_isAtmosphere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(1_000_000, 0, 1_000_000);
        ChunkPos column = new ChunkPos(far);
        if (level.hasChunk(column.x, column.z)) helper.fail("Test column should start unloaded");

        int top = level.getMaxBuildHeight();
        if (!level.getBiome(far.atY(top + 64)).is(ATMOSPHERE)) helper.fail("y=" + (top + 64) + " should be the atmosphere with no chunk loaded");
        if (!level.getBiome(far.atY(5000)).is(ATMOSPHERE)) helper.fail("y=5000 should be the atmosphere with no chunk loaded");
        if (level.getBiome(far.atY(64)).is(ATMOSPHERE)) helper.fail("y=64 is inside the world and keeps the generator's biome");
        if (level.hasChunk(column.x, column.z)) helper.fail("Reading the biome should not load the chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void atmosphere_coolsToMinimumAt2048(GameTestHelper helper) {
        Biome atmosphere = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(ATMOSPHERE);
        if (atmosphere.getModifiedClimateSettings().temperatureModifier() != AtmosphereTemperature.MODIFIER) {
            helper.fail("Atmosphere biome should use the zps:atmosphere modifier");
        }
        assertTemperature(helper, atmosphere, 320, 0.8f);
        assertTemperature(helper, atmosphere, 1184, -0.1f);
        assertTemperature(helper, atmosphere, 2048, AtmosphereTemperature.MIN_TEMPERATURE);
        assertTemperature(helper, atmosphere, 4000, AtmosphereTemperature.MIN_TEMPERATURE);
        helper.succeed();
    }

    private static Holder<Biome> biomeAt(ChunkAccess chunk, BlockPos column, int y) {
        return chunk.getNoiseBiome(QuartPos.fromBlock(column.getX()), QuartPos.fromBlock(y), QuartPos.fromBlock(column.getZ()));
    }

    private static void assertTemperature(GameTestHelper helper, Biome biome, int y, float expected) {
        float actual = ((BiomeInvoker) (Object) biome).zps$getTemperature(new BlockPos(0, y, 0));
        if (Math.abs(actual - expected) > 1.0e-4f) helper.fail("Temperature at y=" + y + " should be " + expected + ", was " + actual);
    }
}
