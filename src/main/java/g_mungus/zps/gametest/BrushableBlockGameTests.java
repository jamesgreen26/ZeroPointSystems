package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.MovedBlockEntityHolder;
import g_mungus.zps.blockentity.ImpactPistonBlockEntity;
import g_mungus.zps.blockentity.SieveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Covers {@code ZPSBrushableBlock} plus the two piston mixins: vanilla suspicious sand and gravel
 * must survive falling and being moved by a piston with their buried loot intact, and the payload
 * must never reach clients while the block is mid-stroke.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class BrushableBlockGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    // The template's stone floor is at relative y=1; y=2 and y=3 are the buildable air layers.

    /** Sand starts on ABOVE_SUPPORT and lands on SUPPORT once the support is pulled. */
    private static final BlockPos SUPPORT = new BlockPos(3, 2, 3);
    private static final BlockPos ABOVE_SUPPORT = new BlockPos(3, 3, 3);

    /** Somewhere on the floor to place and break a block. */
    private static final BlockPos BREAK_POS = new BlockPos(3, 2, 3);

    /** Carpet is not replaceable, so a block landing on it cannot be placed and drops instead. */
    private static final BlockPos CARPET_POS = new BlockPos(5, 2, 3);
    private static final BlockPos HIGH_DROP_POS = new BlockPos(5, 4, 3);

    private static final BlockPos PISTON_POS = new BlockPos(1, 2, 3);
    private static final BlockPos PUSHED_FROM = new BlockPos(2, 2, 3);
    private static final BlockPos PUSHED_TO = new BlockPos(3, 2, 3);
    private static final BlockPos POWER_POS = new BlockPos(1, 3, 3);

    /**
     * The NBT key {@code PistonMovingBlockEntityMixin} writes. Hardcoded rather than shared so the
     * test pins the on-disk/on-wire name.
     */
    private static final String MOVED_BE_KEY = "zps:MovedBlockEntityData";

    /** A piston stroke is 2 ticks; leave slack for the redstone update and the landing tick. */
    private static final int SETTLE_TICKS = 20;

    /** A sieve on the floor, with the drop starting in the air layer above it. */
    private static final BlockPos SIEVE_POS = new BlockPos(3, 2, 3);
    private static final BlockPos SIEVE_DROP_POS = new BlockPos(3, 3, 3);

    /**
     * Vanilla annihilates suspicious sand the moment it starts falling. It should now land like
     * ordinary sand, still holding what was buried in it.
     */
    @GameTest(template = TEMPLATE)
    public static void fall_retainsBuriedItem(GameTestHelper helper) {
        helper.setBlock(SUPPORT, Blocks.STONE);
        helper.setBlock(ABOVE_SUPPORT, Blocks.SUSPICIOUS_SAND);
        bury(helper, ABOVE_SUPPORT, Items.DIAMOND);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(SUPPORT, Blocks.AIR))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertBuried(helper, SUPPORT, Items.DIAMOND))
                .thenSucceed();
    }

    /**
     * Vanilla ships these blocks with {@code PushReaction.DESTROY} and an empty loot table, so a
     * piston deletes them outright. They should now be pushed like gravel.
     */
    @GameTest(template = TEMPLATE)
    public static void pistonPush_retainsBuriedItem(GameTestHelper helper) {
        helper.setBlock(PISTON_POS, facingEast(Blocks.PISTON.defaultBlockState()));
        helper.setBlock(PUSHED_FROM, Blocks.SUSPICIOUS_GRAVEL);
        bury(helper, PUSHED_FROM, Items.EMERALD);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(POWER_POS, Blocks.REDSTONE_BLOCK))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertBuried(helper, PUSHED_TO, Items.EMERALD))
                .thenSucceed();
    }

    /**
     * The retract path runs through {@code moveBlocks(..., extending = false)}, which flips the
     * direction the source position is derived from. Push out, then pull back.
     */
    @GameTest(template = TEMPLATE)
    public static void stickyPistonPull_retainsBuriedItem(GameTestHelper helper) {
        helper.setBlock(PISTON_POS, facingEast(Blocks.STICKY_PISTON.defaultBlockState()));
        helper.setBlock(PUSHED_FROM, Blocks.SUSPICIOUS_SAND);
        bury(helper, PUSHED_FROM, Items.GOLD_INGOT);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(POWER_POS, Blocks.REDSTONE_BLOCK))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertBuried(helper, PUSHED_TO, Items.GOLD_INGOT))
                .thenExecute(() -> helper.setBlock(POWER_POS, Blocks.AIR))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertBuried(helper, PUSHED_FROM, Items.GOLD_INGOT))
                .thenSucceed();
    }

    /**
     * Vanilla ships these blocks with an empty loot table, so breaking one by hand yields nothing.
     * They should drop what they brush into, the same as breaking ordinary sand or gravel.
     */
    @GameTest(template = TEMPLATE)
    public static void breakByHand_dropsSand(GameTestHelper helper) {
        assertBreakDrops(helper, Blocks.SUSPICIOUS_SAND, Items.SAND);
    }

    @GameTest(template = TEMPLATE)
    public static void breakByHand_dropsGravel(GameTestHelper helper) {
        assertBreakDrops(helper, Blocks.SUSPICIOUS_GRAVEL, Items.GRAVEL);
    }

    /**
     * The modded suspicious block has to be a legal host for vanilla's brushable block entity: a
     * block entity whose type does not list its block is discarded on chunk load, taking the buried
     * loot with it. Guards the {@code BlockEntityTypeAddBlocksEvent} wiring in {@code ZPSMod}.
     */
    @GameTest(template = TEMPLATE)
    public static void redSand_isValidBrushableHostAndRetainsBuriedItem(GameTestHelper helper) {
        helper.setBlock(SUPPORT, Blocks.STONE);
        helper.setBlock(ABOVE_SUPPORT, ModBlocks.SUSPICIOUS_RED_SAND.get());

        BlockState state = helper.getBlockState(ABOVE_SUPPORT);
        if (!BlockEntityType.BRUSHABLE_BLOCK.isValid(state)) {
            helper.fail("BlockEntityType.BRUSHABLE_BLOCK does not accept " + state.getBlock()
                    + "; its block entity would be dropped on chunk load");
        }

        bury(helper, ABOVE_SUPPORT, Items.DIAMOND);

        helper.startSequence()
                .thenExecute(() -> helper.setBlock(SUPPORT, Blocks.AIR))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertBuried(helper, SUPPORT, Items.DIAMOND))
                .thenSucceed();
    }

    /** Like the vanilla pair, breaking it by hand yields the block it brushes into. */
    @GameTest(template = TEMPLATE)
    public static void redSand_breakByHand_dropsRedSand(GameTestHelper helper) {
        assertBreakDrops(helper, ModBlocks.SUSPICIOUS_RED_SAND.get(), Items.RED_SAND);
    }

    /**
     * A landing can still fail — here onto a carpet, which is not replaceable. The payload is gone
     * by then, so the drop should be plain sand rather than a hollow suspicious sand item.
     */
    @GameTest(template = TEMPLATE)
    public static void failedLanding_dropsTurnsIntoItem(GameTestHelper helper) {
        helper.setBlock(CARPET_POS, Blocks.WHITE_CARPET);
        helper.setBlock(HIGH_DROP_POS, Blocks.SUSPICIOUS_SAND);
        bury(helper, HIGH_DROP_POS, Items.DIAMOND);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    if (helper.getLevel().getBlockState(helper.absolutePos(CARPET_POS)).getBlock()
                            != Blocks.WHITE_CARPET) {
                        helper.fail("Expected the landing to fail and leave the carpet in place");
                    }
                    assertDropped(helper, CARPET_POS, Items.SAND);
                })
                .thenSucceed();
    }

    /**
     * {@code PistonMovingBlockEntity#getUpdateTag} is {@code saveCustomOnly}, so anything persisted
     * in {@code saveAdditional} is broadcast to every client watching the chunk. The payload must be
     * stripped from the update tag while still being saved to disk.
     */
    @GameTest(template = TEMPLATE)
    public static void movingPiston_doesNotLeakLootToClients(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        BlockState movingState = facingEast(Blocks.MOVING_PISTON.defaultBlockState());
        BlockEntity blockEntity = MovingPistonBlock.newMovingBlockEntity(
                helper.absolutePos(PUSHED_TO),
                movingState,
                Blocks.SUSPICIOUS_SAND.defaultBlockState(),
                Direction.EAST,
                true,
                false);

        if (!(blockEntity instanceof MovedBlockEntityHolder holder)) {
            helper.fail("PistonMovingBlockEntity should implement MovedBlockEntityHolder; "
                    + "PistonMovingBlockEntityMixin did not apply");
            return;
        }

        CompoundTag payload = new CompoundTag();
        payload.put("item", new ItemStack(Items.DIAMOND).save(registries));
        holder.zps$setMovedBlockEntityTag(payload);

        if (blockEntity.getUpdateTag(registries).contains(MOVED_BE_KEY)) {
            helper.fail("Buried loot leaked to clients: " + MOVED_BE_KEY + " is present in getUpdateTag");
        }
        if (!blockEntity.saveWithoutMetadata(registries).contains(MOVED_BE_KEY)) {
            helper.fail("Buried loot is not persisted: " + MOVED_BE_KEY + " is missing from saveWithoutMetadata");
        }
        helper.succeed();
    }

    /**
     * Dropping a suspicious block through a sieve unearths the payload into the sieve's inventory and
     * leaves plain sand falling in its place. Also covers {@code FallingBlockEntityInvoker}, which
     * has no other caller.
     */
    @GameTest(template = TEMPLATE)
    public static void sift_unearthsBuriedLoot(GameTestHelper helper) {
        helper.setBlock(SIEVE_POS, ModBlocks.SIEVE.get());

        FallingBlockEntity falling = FallingBlockEntity.fall(
                helper.getLevel(), helper.absolutePos(SIEVE_DROP_POS), Blocks.SUSPICIOUS_SAND.defaultBlockState());
        CompoundTag payload = new CompoundTag();
        payload.put("item", new ItemStack(Items.DIAMOND).save(helper.getLevel().registryAccess()));
        falling.blockData = payload;

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> assertSifted(helper, SIEVE_POS, Items.DIAMOND))
                // The payload is gone, so what falls on through is ordinary sand. It cannot replace
                // the sieve it lands in, so it drops as an item.
                .thenExecute(() -> assertDropped(helper, SIEVE_POS, Items.SAND))
                .thenSucceed();
    }

    /** Fails unless the sieve at the given position holds the expected item. */
    private static void assertSifted(GameTestHelper helper, BlockPos relativePos, Item expected) {
        if (!(helper.getBlockEntity(relativePos) instanceof SieveBlockEntity sieve)) {
            helper.fail("Expected a sieve block entity at " + relativePos);
            return;
        }

        List<ItemStack> contents = new java.util.ArrayList<>();
        for (int slot = 0; slot < sieve.getInventory().getSlots(); slot++) {
            ItemStack stack = sieve.getInventory().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                contents.add(stack);
            }
        }

        if (contents.stream().noneMatch(stack -> stack.is(expected))) {
            helper.fail("Expected the sieve to hold " + expected + ", found " + contents);
        }
    }

    private static void assertBreakDrops(GameTestHelper helper, Block block, Item expected) {
        helper.setBlock(BREAK_POS, block);
        // GameTestHelper#destroyBlock passes dropBlock = false, so go through the level directly.
        helper.getLevel().destroyBlock(helper.absolutePos(BREAK_POS), true);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> assertDropped(helper, BREAK_POS, expected))
                .thenSucceed();
    }

    /** Scans for a dropped item stack near a position, in absolute coordinates. */
    private static void assertDropped(GameTestHelper helper, BlockPos relativePos, Item expected) {
        BlockPos absolute = helper.absolutePos(relativePos);
        AABB search = new AABB(absolute).inflate(3.0);
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, search);
        for (ItemEntity item : items) {
            if (item.getItem().is(expected)) {
                return;
            }
        }
        helper.fail("Expected a dropped " + expected + " near " + relativePos
                + ", found " + items.stream().map(e -> e.getItem().toString()).toList());
    }

    private static BlockState facingEast(BlockState state) {
        return state.setValue(BlockStateProperties.FACING, Direction.EAST);
    }

    private static void bury(GameTestHelper helper, BlockPos relativePos, Item item) {
        ImpactPistonBlockEntity.buryItem(
                helper.getLevel(), helper.absolutePos(relativePos), new ItemStack(item));
    }

    private static void assertBuried(GameTestHelper helper, BlockPos relativePos, Item expected) {
        BlockPos absolute = helper.absolutePos(relativePos);
        BlockState state = helper.getLevel().getBlockState(absolute);
        if (!(helper.getLevel().getBlockEntity(absolute) instanceof BrushableBlockEntity brushable)) {
            helper.fail("Expected a brushable block at " + relativePos + ", found " + state.getBlock());
            return;
        }
        ItemStack buried = brushable.getItem();
        if (!buried.is(expected) || buried.getCount() != 1) {
            helper.fail("Expected 1x " + expected + " still buried at " + relativePos + ", got " + buried);
        }
    }
}
