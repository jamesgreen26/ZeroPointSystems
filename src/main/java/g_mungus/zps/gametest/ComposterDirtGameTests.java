package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The dirt-filled composter as a farmer's job site. What a villager holds on its workstation is a
 * ticket on the point-of-interest record at that position, so these tests watch the ticket.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ComposterDirtGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos COMPOSTER_POS = new BlockPos(3, 1, 3);

    /**
     * A claimed composter stays claimed through composter -> dirt composter -> composter. Had either
     * swap dropped and re-made the record, the ticket would come back free and the farmer holding
     * it would be left working a site that any other villager could take from under it.
     */
    @GameTest(template = TEMPLATE)
    public static void jobSiteClaim_survivesBothConversions(GameTestHelper helper) {
        BlockState full = Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY);
        helper.setBlock(COMPOSTER_POS, full);
        BlockPos absolute = helper.absolutePos(COMPOSTER_POS);
        PoiManager poi = helper.getLevel().getPoiManager();

        helper.startSequence()
                // PoI changes are queued on the server rather than applied inside setBlock.
                .thenIdle(2)
                .thenExecute(() -> {
                    boolean taken = poi.take(type -> type.is(PoiTypes.FARMER), (type, pos) -> pos.equals(absolute), absolute, 1).isPresent();
                    if (!taken) {
                        helper.fail("Could not claim the composter as a farmer job site", COMPOSTER_POS);
                    }
                    // The same call the Impact Piston makes when it lands.
                    helper.getLevel().setBlockAndUpdate(absolute, ModBlocks.COMPOSTER_DIRT.get().defaultBlockState());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    assertClaimed(helper, poi, absolute, "after packing into dirt");
                    helper.useBlock(COMPOSTER_POS);
                    if (helper.getBlockState(COMPOSTER_POS) != Blocks.COMPOSTER.defaultBlockState()) {
                        helper.fail("Expected an empty composter after use", COMPOSTER_POS);
                    }
                })
                .thenIdle(2)
                .thenExecute(() -> assertClaimed(helper, poi, absolute, "after the dirt was taken out"))
                .thenSucceed();
    }

    /**
     * Placed cold, a dirt composter is a farmer job site with its one ticket free, and it passes the
     * two predicates villager AI goes by: the one an unemployed villager searches for work with, and
     * the one a farmer keeps re-checking its workstation against.
     */
    @GameTest(template = TEMPLATE)
    public static void dirtComposter_isAFarmerJobSite(GameTestHelper helper) {
        helper.setBlock(COMPOSTER_POS, ModBlocks.COMPOSTER_DIRT.get());
        BlockPos absolute = helper.absolutePos(COMPOSTER_POS);
        PoiManager poi = helper.getLevel().getPoiManager();

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    if (!poi.existsAtPosition(PoiTypes.FARMER, absolute)) {
                        helper.fail("Expected a farmer job site at the dirt composter, got " + poi.getType(absolute), COMPOSTER_POS);
                    }
                    if (!poi.exists(absolute, VillagerProfession.NONE.acquirableJobSite())) {
                        helper.fail("An unemployed villager would not consider the dirt composter a job", COMPOSTER_POS);
                    }
                    if (!poi.exists(absolute, VillagerProfession.FARMER.heldJobSite())) {
                        helper.fail("A farmer would not accept the dirt composter as its workstation", COMPOSTER_POS);
                    }
                    if (poi.getFreeTickets(absolute) != 1) {
                        helper.fail("Expected 1 free ticket, got " + poi.getFreeTickets(absolute), COMPOSTER_POS);
                    }
                })
                .thenSucceed();
    }

    /** A hopper underneath pulls the dirt out, once, and leaves an empty vanilla composter. */
    @GameTest(template = TEMPLATE)
    public static void hopperBelow_pullsTheDirtOut(GameTestHelper helper) {
        BlockPos hopperPos = COMPOSTER_POS;
        BlockPos composterPos = COMPOSTER_POS.above();
        helper.setBlock(hopperPos, Blocks.HOPPER);
        helper.setBlock(composterPos, ModBlocks.COMPOSTER_DIRT.get());

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    if (helper.getBlockState(composterPos) != Blocks.COMPOSTER.defaultBlockState()) {
                        helper.fail("Expected an empty composter above the hopper, got " + helper.getBlockState(composterPos), composterPos);
                    }
                    if (!(helper.getBlockEntity(hopperPos) instanceof HopperBlockEntity hopper)) {
                        helper.fail("Expected a hopper", hopperPos);
                        return;
                    }
                    if (hopper.countItem(Items.DIRT) != 1) {
                        helper.fail("Expected exactly 1 dirt in the hopper, got " + hopper.countItem(Items.DIRT), hopperPos);
                    }
                })
                .thenSucceed();
    }

    /**
     * The item handler other mods' pipes see: dirt from the bottom face only, and a handler that was
     * fetched before the dirt left by another route has nothing more to give.
     */
    @GameTest(template = TEMPLATE)
    public static void itemHandler_offersDirtFromBelowOnly(GameTestHelper helper) {
        helper.setBlock(COMPOSTER_POS, ModBlocks.COMPOSTER_DIRT.get());
        BlockPos absolute = helper.absolutePos(COMPOSTER_POS);

        IItemHandler side = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absolute, Direction.NORTH);
        if (side != null && side.getSlots() != 0) {
            helper.fail("The side faces should expose no slots", COMPOSTER_POS);
        }
        IItemHandler below = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absolute, Direction.DOWN);
        if (below == null || below.getSlots() != 1 || !below.extractItem(0, 1, true).is(Items.DIRT)) {
            helper.fail("Expected one dirt on offer through the bottom face", COMPOSTER_POS);
            return;
        }

        helper.useBlock(COMPOSTER_POS);
        for (int slot = 0; slot < below.getSlots(); slot++) {
            if (!below.extractItem(slot, 1, false).isEmpty()) {
                helper.fail("A stale handler handed out dirt that had already been taken by hand", COMPOSTER_POS);
            }
        }
        helper.succeed();
    }

    /**
     * A comparator reads a dirt-filled composter at full strength, and follows it through both
     * conversions: 8 for the full vanilla composter before, nothing for the empty one after.
     */
    @GameTest(template = TEMPLATE)
    public static void comparator_readsFullStrength(GameTestHelper helper) {
        BlockPos comparatorPos = COMPOSTER_POS.south();
        BlockState full = Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY);
        helper.setBlock(comparatorPos.below(), Blocks.STONE);
        // A comparator reads the block it faces. The test structure may be rotated, which moves
        // positions but not block states, so the facing is worked out in world space.
        BlockPos toComposter = helper.absolutePos(COMPOSTER_POS).subtract(helper.absolutePos(comparatorPos));
        Direction facing = Direction.fromDelta(toComposter.getX(), toComposter.getY(), toComposter.getZ());
        helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, facing));
        // A comparator set down by setBlock takes no first reading, so the composter goes in after it.
        helper.setBlock(COMPOSTER_POS, full);

        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    assertComparator(helper, comparatorPos, 8, "reading a full vanilla composter");
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(COMPOSTER_POS), ModBlocks.COMPOSTER_DIRT.get().defaultBlockState());
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    assertComparator(helper, comparatorPos, 9, "reading a dirt-filled composter");
                    helper.useBlock(COMPOSTER_POS);
                })
                .thenIdle(4)
                .thenExecute(() -> assertComparator(helper, comparatorPos, 0, "after the dirt was taken out"))
                .thenSucceed();
    }

    private static void assertComparator(GameTestHelper helper, BlockPos comparatorPos, int expected, String when) {
        if (!(helper.getBlockEntity(comparatorPos) instanceof ComparatorBlockEntity comparator)) {
            helper.fail("Expected a comparator", comparatorPos);
            return;
        }
        if (comparator.getOutputSignal() != expected) {
            helper.fail("Expected comparator output " + expected + " " + when + ", got " + comparator.getOutputSignal(), comparatorPos);
        }
    }

    private static void assertClaimed(GameTestHelper helper, PoiManager poi, BlockPos absolute, String when) {
        if (!poi.existsAtPosition(PoiTypes.FARMER, absolute)) {
            helper.fail("Farmer job site missing " + when + ", got " + poi.getType(absolute), COMPOSTER_POS);
        }
        if (poi.getFreeTickets(absolute) != 0) {
            helper.fail("The claim on the job site was dropped " + when, COMPOSTER_POS);
        }
    }
}
