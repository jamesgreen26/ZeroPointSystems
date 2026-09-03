package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.ImpactPistonBlockEntity;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ImpactInput;
import g_mungus.zps.recipe.ImpactRecipe;
import g_mungus.zps.recipe.ImpactResult;
import g_mungus.zps.recipe.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ImpactPistonGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos PISTON_POS = new BlockPos(3, 2, 3);
    private static final BlockPos TARGET_POS = PISTON_POS.below();
    private static final BlockPos POWER_POS = new BlockPos(4, 2, 3);

    /** One full stroke plus slack for the fall and the landing tick. */
    private static final int STROKE_TICKS = ImpactPistonBlockEntity.RAISE_TICKS + ImpactPistonBlockEntity.FALL_TICKS + 4;
    /** Long enough that a working piston would have struck several times over. */
    private static final int IDLE_OBSERVATION_TICKS = 60;

    /** The happy path: powered and fed, the piston cracks the bricks beneath it. */
    @GameTest(template = TEMPLATE)
    public static void converts_whenPoweredAndFed(GameTestHelper helper) {
        helper.setBlock(TARGET_POS, Blocks.STONE_BRICKS);
        ImpactPistonBlockEntity piston = placePiston(helper);
        charge(piston);
        helper.setBlock(POWER_POS, Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(STROKE_TICKS)
                .thenExecute(() -> assertTarget(helper, Blocks.CRACKED_STONE_BRICKS))
                .thenSucceed();
    }

    /** Redstone is the gate: fed but unpowered, nothing happens. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void doesNothing_withoutRedstone(GameTestHelper helper) {
        helper.setBlock(TARGET_POS, Blocks.STONE_BRICKS);
        charge(placePiston(helper));

        helper.startSequence()
                .thenIdle(IDLE_OBSERVATION_TICKS)
                .thenExecute(() -> assertTarget(helper, Blocks.STONE_BRICKS))
                .thenSucceed();
    }

    /** Powered but with no FE, the rod never leaves the bottom of its stroke. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void doesNothing_withoutEnergy(GameTestHelper helper) {
        helper.setBlock(TARGET_POS, Blocks.STONE_BRICKS);
        placePiston(helper);
        helper.setBlock(POWER_POS, Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(IDLE_OBSERVATION_TICKS)
                .thenExecute(() -> assertTarget(helper, Blocks.STONE_BRICKS))
                .thenSucceed();
    }

    /** A block with no impact recipe is left alone, and costs the piston nothing to ignore. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ignores_blockWithoutRecipe(GameTestHelper helper) {
        helper.setBlock(TARGET_POS, Blocks.DIRT);
        ImpactPistonBlockEntity piston = placePiston(helper);
        charge(piston);
        int energyBefore = piston.getEnergyStored();
        helper.setBlock(POWER_POS, Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(IDLE_OBSERVATION_TICKS)
                .thenExecute(() -> {
                    assertTarget(helper, Blocks.DIRT);
                    if (piston.getEnergyStored() != energyBefore) {
                        helper.fail("Piston drew " + (energyBefore - piston.getEnergyStored())
                                + " FE idling over a block with no recipe; expected none");
                    }
                })
                .thenSucceed();
    }

    /** What the cobblestone recipe may bury: copper, lithium and iron nuggets, nothing else. */
    private static final TagKey<Item> RESOURCES_IN_COBBLESTONE = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("zps", "resources_in_cobblestone"));

    /**
     * The shipped cobblestone recipe must actually be a 19:1 gravel / suspicious gravel split with
     * one or two nuggets buried in the rare outcome. Guards the data files and the recipe codec
     * together.
     */
    @GameTest(template = TEMPLATE)
    public static void cobblestoneRecipe_isWeightedWithBuriedNugget(GameTestHelper helper) {
        ImpactRecipe recipe = findRecipe(helper, Blocks.COBBLESTONE);
        List<ImpactResult> results = recipe.results();
        if (results.size() != 2) {
            helper.fail("Expected 2 cobblestone outcomes, got " + results.size());
        }

        ImpactResult gravel = outcomeFor(helper, results, Blocks.GRAVEL);
        ImpactResult suspicious = outcomeFor(helper, results, Blocks.SUSPICIOUS_GRAVEL);
        if (gravel.weight() != 19 || suspicious.weight() != 1) {
            helper.fail("Expected a 19:1 gravel/suspicious split, got " + gravel.weight() + ":" + suspicious.weight());
        }
        if (!gravel.buriedItem().isEmpty()) {
            helper.fail("Plain gravel should have nothing buried in it");
        }

        Ingredient buried = suspicious.buriedItem()
                .orElseGet(() -> {
                    helper.fail("Suspicious gravel outcome has no buried item");
                    return Ingredient.EMPTY;
                });
        if (!buried.test(new ItemStack(Items.IRON_NUGGET))) {
            helper.fail("Buried item should accept iron nuggets (the " + RESOURCES_IN_COBBLESTONE.location() + " tag)");
        }
        // The point of the narrower tag: forge:nuggets would sweep in every other mod's nuggets too.
        if (buried.test(new ItemStack(ModItems.ALUMINUM_NUGGET.get()))) {
            helper.fail("Buried item should not accept aluminum nuggets: " + RESOURCES_IN_COBBLESTONE.location()
                    + " is deliberately narrower than forge:nuggets");
        }
        for (ItemStack candidate : buried.getItems()) {
            if (!candidate.is(RESOURCES_IN_COBBLESTONE)) {
                helper.fail("Buried candidate " + candidate + " is outside the " + RESOURCES_IN_COBBLESTONE.location() + " tag");
            }
        }

        IntProvider count = suspicious.count();
        if (count.getMinValue() != 1 || count.getMaxValue() != 2) {
            helper.fail("Expected 1 to 2 nuggets buried, got " + count.getMinValue() + " to " + count.getMaxValue());
        }
        if (gravel.count().getMaxValue() != 1) {
            helper.fail("Plain gravel buries nothing, so its count should stay at the default 1");
        }
        helper.succeed();
    }

    /** The weighted pick must track the declared weights rather than picking uniformly. */
    @GameTest(template = TEMPLATE)
    public static void weightedPick_followsDeclaredWeights(GameTestHelper helper) {
        Holder<Block> common = BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.GRAVEL);
        Holder<Block> rare = BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.SUSPICIOUS_GRAVEL);
        ImpactRecipe recipe = new ImpactRecipe(
                ZPSMod.resource("gametest/weighted_pick"),
                HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBBLESTONE)),
                List.of(new ImpactResult(common, 9, Optional.empty()),
                        new ImpactResult(rare, 1, Optional.empty())));

        int samples = 10000;
        RandomSource random = RandomSource.create(0xC0FFEEL);
        int rareCount = 0;
        for (int i = 0; i < samples; i++) {
            if (recipe.pick(random).block() == rare) {
                rareCount++;
            }
        }

        // 10% of 10000, with generous slack: uniform picking would land at ~5000 and fail loudly.
        if (rareCount < 850 || rareCount > 1150) {
            helper.fail("Expected the 1-in-10 outcome ~1000 times in " + samples + " picks, got " + rareCount);
        }
        helper.succeed();
    }

    /**
     * Pins the vanilla behaviour the suspicious-gravel outcome relies on: a stack written into a
     * brushable block entity's {@code item} tag is the stack it later hands back.
     */
    @GameTest(template = TEMPLATE)
    public static void buryItem_storesStackInBrushableBlock(GameTestHelper helper) {
        helper.setBlock(TARGET_POS, Blocks.SUSPICIOUS_GRAVEL);
        BlockPos absolute = helper.absolutePos(TARGET_POS);
        ImpactPistonBlockEntity.buryItem(helper.getLevel(), absolute, new ItemStack(Items.GOLD_NUGGET));

        if (!(helper.getLevel().getBlockEntity(absolute) instanceof BrushableBlockEntity brushable)) {
            helper.fail("Expected a brushable block entity at " + TARGET_POS);
            return;
        }
        ItemStack buried = brushable.getItem();
        if (!buried.is(Items.GOLD_NUGGET) || buried.getCount() != 1) {
            helper.fail("Expected 1x gold nugget buried in the suspicious gravel, got " + buried);
        }
        helper.succeed();
    }

    private static ImpactPistonBlockEntity placePiston(GameTestHelper helper) {
        helper.setBlock(PISTON_POS, ModBlocks.IMPACT_PISTON.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(PISTON_POS));
        if (!(blockEntity instanceof ImpactPistonBlockEntity piston)) {
            helper.fail("Expected an impact piston at " + PISTON_POS + ", got " + blockEntity);
            throw new IllegalStateException("unreachable");
        }
        return piston;
    }

    /** Fills the piston's buffer. The storage caps each transfer, so this takes several passes. */
    private static void charge(ImpactPistonBlockEntity piston) {
        var storage = piston.getEnergyStorage();
        while (storage.receiveEnergy(ImpactPistonBlockEntity.ENERGY_CAPACITY, false) > 0) {
            // keep filling
        }
    }

    private static void assertTarget(GameTestHelper helper, Block expected) {
        BlockState actual = helper.getBlockState(TARGET_POS);
        if (!actual.is(expected)) {
            helper.fail("Expected " + expected.getName().getString() + " below the piston, got "
                    + actual.getBlock().getName().getString(), TARGET_POS);
        }
    }

    private static ImpactRecipe findRecipe(GameTestHelper helper, Block block) {
        return helper.getLevel().getRecipeManager()
                .getRecipeFor(ModRecipes.IMPACT_TYPE.get(), new ImpactInput(block.defaultBlockState()), helper.getLevel())
                .orElseGet(() -> {
                    helper.fail("No impact recipe registered for " + block.getName().getString());
                    throw new IllegalStateException("unreachable");
                });
    }

    private static ImpactResult outcomeFor(GameTestHelper helper, List<ImpactResult> results, Block block) {
        return results.stream()
                .filter(result -> result.block().value() == block)
                .findFirst()
                .orElseGet(() -> {
                    helper.fail("No outcome producing " + block.getName().getString());
                    throw new IllegalStateException("unreachable");
                });
    }
}
