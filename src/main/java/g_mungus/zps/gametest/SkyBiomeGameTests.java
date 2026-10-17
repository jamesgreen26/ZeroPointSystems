package g_mungus.zps.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import g_mungus.zps.ZPSMod;
import g_mungus.zps.mixin.BiomeInvoker;
import g_mungus.zps.world.AtmosphereTemperature;
import net.minecraft.commands.CommandSourceStack;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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

    /** A sky biome is found straight up from where it is looked for, in the bottom quart of its band. */
    @GameTest(template = TEMPLATE)
    public static void locatingASkyBiome_findsItOverhead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(3, 2, 3));
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome -> biome.is(ATMOSPHERE), from, 6400, 32, 64);
        if (found == null) {
            helper.fail("The atmosphere should be found");
            return;
        }
        BlockPos at = found.getFirst();
        int top = level.getMaxBuildHeight();
        if (at.getX() != from.getX() || at.getZ() != from.getZ()) helper.fail("The atmosphere is straight up, not at " + at);
        if (at.getY() != top + 2) helper.fail("The atmosphere's first quart is just above the build limit, y=" + (top + 2) + ", not y=" + at.getY());
        if (!level.getBiome(at).is(ATMOSPHERE)) helper.fail("Where it was found should be the atmosphere");
        helper.succeed();
    }

    /** The command itself, as a player would run it. */
    @GameTest(template = TEMPLATE)
    public static void locateBiomeCommand_findsASkyBiome(GameTestHelper helper) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))))
                .withSuppressedOutput();
        try {
            int result = helper.getLevel().getServer().getCommands().getDispatcher()
                    .execute("locate biome zps:overworld_atmosphere", source);
            if (result <= 0) helper.fail("/locate biome should find the atmosphere, returned " + result);
        } catch (CommandSyntaxException e) {
            helper.fail("/locate biome should find the atmosphere: " + e.getMessage());
        }
        helper.succeed();
    }

    /** Biomes on the ground are still found on the ground. */
    @GameTest(template = TEMPLATE)
    public static void locatingAGroundBiome_stillFindsItOnTheGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(3, 2, 3));
        Holder<Biome> here = level.getBiome(from);
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome -> biome.equals(here), from, 6400, 32, 64);
        if (found == null) {
            helper.fail("The biome underfoot should be found");
            return;
        }
        if (found.getFirst().getY() >= level.getMaxBuildHeight()) helper.fail("The biome underfoot is on the ground, not at " + found.getFirst());
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
