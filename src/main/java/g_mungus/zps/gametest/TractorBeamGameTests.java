package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.BeamCollectorBlock;
import g_mungus.zps.blockentity.BeamCollectorBlockEntity;
import g_mungus.zps.mixin.FallingBlockEntityInvoker;
import g_mungus.zps.tractor.BeamScan;
import g_mungus.zps.tractor.PlayerWeightlessness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TractorBeamGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    /** Placing a block calls for a connectivity update on the next tick; give it a couple. */
    private static final int FORM_TICKS = 3;

    /** A lone beam facing east, two blocks up so its column clears the floor, with its power behind it. */
    private static final BlockPos BEAM = new BlockPos(1, 2, 5);
    private static final BlockPos POWER = new BlockPos(0, 2, 5);

    // --- structure --------------------------------------------------------------------------------

    /** Nine blocks sharing a facing form one 3x3 panel with the rim flagged only on the outside. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void panel_3x3_formsOneStructure(GameTestHelper helper) {
        BlockPos corner = new BlockPos(1, 1, 4);
        placePanel(helper, corner, Direction.EAST, 3, 3);

        helper.runAfterDelay(FORM_TICKS, () -> {
            BeamCollectorBlockEntity controller = beam(helper, corner);
            helper.assertTrue(controller.isController(), "The minimum corner should be the controller");
            helper.assertTrue(controller.getWidth() == 3 && controller.getHeight() == 1,
                    "Expected a 3x3x1 panel, got " + controller.getWidth() + "x" + controller.getHeight());
            helper.assertTrue(controller.getInventory().getSlots() == 21,
                    "A 3x3 panel should pool 21 slots, has " + controller.getInventory().getSlots());
            helper.assertTrue(controller.getMaxRange() == 30, "A 3x3 panel should reach 30 at full signal, reaches "
                    + controller.getMaxRange());

            BlockState centre = helper.getBlockState(corner.offset(0, 1, 1));
            helper.assertTrue(centre.getValue(BeamCollectorBlock.SIZE) == 3 && centre.getValue(BeamCollectorBlock.COLUMN) == 1
                            && centre.getValue(BeamCollectorBlock.ROW) == 1,
                    "The centre block should sit in the middle of a size 3 panel");

            // Facing east, the panel's right is south: the far corner is top right, and row 0 is the top.
            BlockState farCorner = helper.getBlockState(corner.offset(0, 2, 2));
            helper.assertTrue(farCorner.getValue(BeamCollectorBlock.SIZE) == 3 && farCorner.getValue(BeamCollectorBlock.COLUMN) == 2
                            && farCorner.getValue(BeamCollectorBlock.ROW) == 0,
                    "The top right block should be column 2, row 0");
            helper.succeed();
        });
    }

    /** Back-to-back panels share an axis, which is all the multiblock handler looks at by itself. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void oppositeFacings_doNotMerge(GameTestHelper helper) {
        BlockPos corner = new BlockPos(1, 1, 4);
        for (int y = 0; y < 2; y++) {
            place(helper, corner.offset(0, y, 0), Direction.EAST);
            place(helper, corner.offset(0, y, 1), Direction.WEST);
        }

        helper.runAfterDelay(FORM_TICKS, () -> {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BeamCollectorBlockEntity part = beam(helper, corner.offset(0, y, z));
                    helper.assertTrue(part.isController() && part.getWidth() == 1,
                            "Blocks facing opposite ways should stay apart");
                }
            }
            helper.succeed();
        });
    }

    /**
     * Two candidate 2x2s overlap in a 2x3 and only one can win. The loser used to be resized to a zero-block
     * structure; it has to come out as ordinary single blocks whichever way the tie falls.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void panel_2x3_formsOneSquareAndTwoSingles(GameTestHelper helper) {
        BlockPos corner = new BlockPos(1, 1, 4);
        placePanel(helper, corner, Direction.EAST, 3, 2);

        helper.runAfterDelay(FORM_TICKS + 2, () -> {
            int squares = 0;
            int singles = 0;
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 2; z++) {
                    BeamCollectorBlockEntity part = beam(helper, corner.offset(0, y, z));
                    helper.assertTrue(part.getWidth() >= 1 && part.getHeight() == 1,
                            "Every block should keep a sane size, got " + part.getWidth() + "x" + part.getHeight());
                    if (!part.isController()) {
                        continue;
                    }
                    if (part.getWidth() == 2) {
                        squares++;
                        helper.assertTrue(part.getInventory().getSlots() == 15, "A 2x2 panel should pool 15 slots");
                    } else {
                        singles++;
                        helper.assertTrue(part.getInventory().getSlots() == 9, "A single block should have 9 slots");
                        helper.assertTrue(part.getMaxEnergyStored() > 0, "A single block should keep its buffer");
                    }
                }
            }
            helper.assertTrue(squares == 1 && singles == 2,
                    "Expected one 2x2 and two singles, got " + squares + " and " + singles);
            helper.succeed();
        });
    }

    /** Breaking a block hands the pool round the blocks left behind: nothing is lost, nothing duplicated. */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void brokenPanel_keepsItsContents(GameTestHelper helper) {
        BlockPos corner = new BlockPos(1, 1, 4);
        placePanel(helper, corner, Direction.EAST, 2, 2);

        helper.runAfterDelay(FORM_TICKS, () -> {
            ItemStackHandler inventory = beam(helper, corner).getInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                inventory.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            helper.destroyBlock(corner.offset(0, 1, 1));

            helper.runAfterDelay(FORM_TICKS + 2, () -> {
                int held = 0;
                for (BlockPos pos : new BlockPos[]{corner, corner.offset(0, 1, 0), corner.offset(0, 0, 1)}) {
                    BeamCollectorBlockEntity part = beam(helper, pos);
                    if (part.isController()) {
                        held += count(part.getInventory(), Items.COBBLESTONE);
                    }
                }
                int loose = countLoose(helper, Items.COBBLESTONE);
                helper.assertTrue(held + loose == 15 * 64,
                        "Expected all 960 cobblestone to survive, found " + held + " held and " + loose + " loose");
                helper.succeed();
            });
        });
    }

    /** Four singles hold 36 stacks between them and a 2x2 holds 15; the difference drops, it does not vanish. */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void mergingFullSingles_dropsTheOverflow(GameTestHelper helper) {
        BlockPos corner = new BlockPos(1, 1, 4);
        // Opposite facings keep them apart while they are filled.
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 2; z++) {
                place(helper, corner.offset(0, y, z), (y + z) % 2 == 0 ? Direction.EAST : Direction.WEST);
            }
        }

        helper.runAfterDelay(FORM_TICKS, () -> {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos pos = corner.offset(0, y, z);
                    ItemStackHandler inventory = beam(helper, pos).getInventory();
                    for (int slot = 0; slot < inventory.getSlots(); slot++) {
                        inventory.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
                    }
                    helper.setBlock(pos, helper.getBlockState(pos).setValue(BeamCollectorBlock.FACING, Direction.EAST));
                }
            }
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    beam(helper, corner.offset(0, y, z)).requestConnectivityUpdate();
                }
            }

            helper.runAfterDelay(FORM_TICKS + 2, () -> {
                BeamCollectorBlockEntity controller = beam(helper, corner);
                helper.assertTrue(controller.getWidth() == 2, "The four blocks should have merged");
                int held = count(controller.getInventory(), Items.COBBLESTONE);
                int loose = countLoose(helper, Items.COBBLESTONE);
                helper.assertTrue(held == 15 * 64, "The 2x2 should be full with 15 stacks, holds " + held);
                helper.assertTrue(loose == 21 * 64, "The other 21 stacks should have dropped, found " + loose);
                helper.succeed();
            });
        });
    }

    // --- settings, energy, automation -------------------------------------------------------------

    /**
     * Range is set by redstone strength, each size spreading the fifteen steps over its own reach: a single block
     * gains a block of range on every odd strength, a 2x2 has one block a step, a 3x3 two.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void range_followsRedstoneStrength_bySize(GameTestHelper helper) {
        int[] single = {0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8};
        for (int strength = 0; strength <= 15; strength++) {
            helper.assertTrue(BeamCollectorBlockEntity.rangeFor(1, strength) == single[strength],
                    "A single block at strength " + strength + " should reach " + single[strength]);
            helper.assertTrue(BeamCollectorBlockEntity.rangeFor(2, strength) == strength,
                    "A 2x2 at strength " + strength + " should reach " + strength);
            helper.assertTrue(BeamCollectorBlockEntity.rangeFor(3, strength) == 2 * strength,
                    "A 3x3 at strength " + strength + " should reach " + 2 * strength);
        }
        helper.succeed();
    }

    /** The same, in the world: a full-strength signal, a weak one from a comparator, and none. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void range_isReadFromTheSignalAtThePanel(GameTestHelper helper) {
        BlockPos strong = BEAM;
        BlockPos weak = new BlockPos(5, 2, 5);
        BlockPos unpowered = new BlockPos(8, 2, 5);
        place(helper, strong, Direction.EAST);
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        place(helper, weak, Direction.EAST);
        powerWithStrength(helper, weak, 5);
        place(helper, unpowered, Direction.EAST);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(beam(helper, strong).getRange() == 8,
                    "A redstone block should give a single block its full 8, got " + beam(helper, strong).getRange());
            helper.assertTrue(beam(helper, weak).getSignal() == 5 && beam(helper, weak).getRange() == 3,
                    "Strength 5 should give a single block 3, got strength " + beam(helper, weak).getSignal()
                            + " and range " + beam(helper, weak).getRange());
            helper.assertTrue(beam(helper, unpowered).getRange() == 0, "No signal, no beam");
            helper.succeed();
        });
    }

    /** Automation takes from the panel and cannot put into it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void automation_isExtractOnly(GameTestHelper helper) {
        place(helper, BEAM, Direction.EAST);
        helper.runAfterDelay(FORM_TICKS, () -> {
            beam(helper, BEAM).getInventory().setStackInSlot(0, new ItemStack(Items.DIRT, 5));
            IItemHandler handler = helper.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK, helper.absolutePos(BEAM), Direction.DOWN);
            helper.assertTrue(handler != null, "The panel should expose an item handler");
            ItemStack refused = handler.insertItem(1, new ItemStack(Items.STONE, 4), false);
            helper.assertTrue(refused.getCount() == 4, "Insertion should be refused");
            ItemStack taken = handler.extractItem(0, 5, false);
            helper.assertTrue(taken.is(Items.DIRT) && taken.getCount() == 5, "Extraction should work");
            helper.succeed();
        });
    }

    /** No redstone, or no energy: nothing moves. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void idleBeam_doesNothing(GameTestHelper helper) {
        // Powered, but empty.
        place(helper, BEAM, Direction.EAST);
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        // Full, but unpowered.
        BlockPos second = new BlockPos(1, 2, 8);
        place(helper, second, Direction.EAST);
        helper.setBlock(BEAM.east(2), Blocks.DIRT);
        helper.setBlock(second.east(2), Blocks.DIRT);

        helper.runAfterDelay(FORM_TICKS, () -> fill(helper, second));
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.DIRT, BEAM.east(2));
            helper.assertBlockPresent(Blocks.DIRT, second.east(2));
            helper.assertFalse(beam(helper, BEAM).isActive(), "A beam without energy should not run");
            helper.assertFalse(beam(helper, second).isActive(), "A beam without redstone should not run");
            helper.succeed();
        });
    }

    /** The running cost is set by size and range alone: pulling blocks loose costs nothing on top. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void runningCost_isTheSameWhilePullingBlocks(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        for (int distance = 2; distance <= 5; distance++) {
            helper.setBlock(BEAM.east(distance), Blocks.DIRT);
        }

        helper.runAfterDelay(FORM_TICKS + 2, () -> {
            int before = beam.getEnergyStored();
            int cost = beam.getTickCost();
            helper.assertTrue(cost == 64, "One column of eight blocks should cost 64 FE a tick, costs " + cost);
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(count(beam.getInventory(), Items.DIRT) >= 1,
                        "The beam should have been pulling blocks in while the cost was measured");
                int spent = before - beam.getEnergyStored();
                helper.assertTrue(spent == 40 * cost, "Expected " + 40 * cost + " FE over 40 ticks, spent " + spent);
                helper.succeed();
            });
        });
    }

    // --- items ------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void item_isPulledInAndCollected(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        helper.spawnItem(Items.DIAMOND, 6.5f, 2.5f, 5.5f);

        helper.succeedWhen(() -> helper.assertTrue(count(beam.getInventory(), Items.DIAMOND) == 1,
                "The diamond should end up in the panel"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void item_beyondTheRangeTheSignalSets_isLeftAlone(GameTestHelper helper) {
        place(helper, BEAM, Direction.EAST);
        // Strength 5 on a single block is a range of 3.
        powerWithStrength(helper, BEAM, 5);
        fill(helper, BEAM);
        BeamCollectorBlockEntity beam = beam(helper, BEAM);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(beam.getRange() == 3, "Expected a range of 3, got " + beam.getRange());
            helper.spawnItem(Items.DIAMOND, 7.5f, 2.5f, 5.5f);
        });

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(count(beam.getInventory(), Items.DIAMOND) == 0, "The diamond was out of range");
            helper.assertTrue(countLoose(helper, Items.DIAMOND) == 1, "The diamond should still be lying there");
            helper.succeed();
        });
    }

    /** What stops a column also shields what is behind it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void item_behindAnImmovableBlock_isLeftAlone(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        helper.setBlock(BEAM.east(3), Blocks.OBSIDIAN);
        helper.spawnItem(Items.DIAMOND, 6.5f, 2.5f, 5.5f);

        helper.runAfterDelay(70, () -> {
            helper.assertBlockPresent(Blocks.OBSIDIAN, BEAM.east(3));
            helper.assertTrue(count(beam.getInventory(), Items.DIAMOND) == 0, "The obsidian should shield the diamond");
            helper.succeed();
        });
    }

    // --- blocks -----------------------------------------------------------------------------------

    /** The nearest pushable block comes loose and is collected as itself; obsidian ends the column. */
    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void block_isPulledLooseAndCollected_obsidianShieldsTheRest(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        helper.setBlock(BEAM.east(2), Blocks.GRASS_BLOCK);
        helper.setBlock(BEAM.east(4), Blocks.OBSIDIAN);
        helper.setBlock(BEAM.east(5), Blocks.DIRT);

        helper.runAfterDelay(120, () -> {
            helper.assertBlockPresent(Blocks.AIR, BEAM.east(2));
            helper.assertBlockPresent(Blocks.AIR, BEAM.east(1));
            helper.assertBlockPresent(Blocks.OBSIDIAN, BEAM.east(4));
            helper.assertBlockPresent(Blocks.DIRT, BEAM.east(5));
            helper.assertTrue(count(beam.getInventory(), Items.GRASS_BLOCK) == 1,
                    "The grass block should be collected as a grass block");
            helper.succeed();
        });
    }

    /**
     * The rays that find blocks in other grids, made to run where there are none. They can then only find what
     * the walk of the panel's own grid finds, so the beam must behave exactly as it always does: break the grass,
     * which has an outline and no substance, then pull the stone behind it, and leave what the obsidian shields.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "tractorBeamRays")
    public static void rays_agreeWithTheWalk(GameTestHelper helper) {
        BeamScan.castEverywhere = true;
        BeamCollectorBlockEntity beam = runningBeam(helper);
        helper.setBlock(BEAM.east(3), Blocks.SHORT_GRASS);
        helper.setBlock(BEAM.east(5), Blocks.STONE);
        helper.setBlock(BEAM.east(6), Blocks.OBSIDIAN);
        helper.setBlock(BEAM.east(7), Blocks.DIRT);

        helper.runAfterDelay(150, () -> {
            BeamScan.castEverywhere = false;
            helper.assertBlockPresent(Blocks.AIR, BEAM.east(1));
            helper.assertBlockPresent(Blocks.AIR, BEAM.east(3));
            helper.assertBlockPresent(Blocks.AIR, BEAM.east(5));
            helper.assertBlockPresent(Blocks.OBSIDIAN, BEAM.east(6));
            helper.assertBlockPresent(Blocks.DIRT, BEAM.east(7));
            helper.assertTrue(count(beam.getInventory(), Items.STONE) == 1, "The stone should be collected");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fullInventory_stopsPullingBlocks(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        // Filled before the first tick, so the beam never sees room for the dirt.
        for (int slot = 0; slot < beam.getInventory().getSlots(); slot++) {
            beam.getInventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.setBlock(BEAM.east(2), Blocks.DIRT);

        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.DIRT, BEAM.east(2));
            helper.succeed();
        });
    }

    /** Pistons cannot push block entities, so neither can the beam; the chest ends the column. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void blockEntity_isNotPulled(GameTestHelper helper) {
        runningBeam(helper);
        helper.setBlock(BEAM.east(2), Blocks.CHEST);
        helper.setBlock(BEAM.east(3), Blocks.DIRT);

        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.CHEST, BEAM.east(2));
            helper.assertBlockPresent(Blocks.DIRT, BEAM.east(3));
            helper.succeed();
        });
    }

    /**
     * A carried block passes through things, so that it cannot jam on its way in. That must end with the beam: a
     * block that kept it would fall through the floor and be lost.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void carriedBlock_collidesAgainOnceTheBeamStops(GameTestHelper helper) {
        runningBeam(helper);
        helper.setBlock(BEAM.east(7), Blocks.DIRT);

        helper.runAfterDelay(8, () -> {
            List<FallingBlockEntity> carried = helper.getLevel()
                    .getEntitiesOfClass(FallingBlockEntity.class, helper.getBounds());
            helper.assertTrue(carried.size() == 1, "The dirt should be on its way in, found " + carried.size());
            helper.assertTrue(carried.get(0).noPhysics, "A carried block should not collide");
            helper.setBlock(POWER, Blocks.AIR);

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class,
                        helper.getBounds().inflate(0, 64, 0)).isEmpty(), "The dirt should have come to rest");
                boolean landed = false;
                for (int x = 2; x <= 8; x++) {
                    for (int y = 1; y <= 3; y++) {
                        landed |= helper.getBlockState(new BlockPos(x, y, 5)).is(Blocks.DIRT);
                    }
                }
                helper.assertTrue(landed || countLoose(helper, Items.DIRT) == 1,
                        "The dirt should have landed on the floor, not fallen through it; floor is "
                                + helper.getBlockState(new BlockPos(5, 0, 5)) + " / "
                                + helper.getBlockState(new BlockPos(5, 1, 5)));
                helper.succeed();
            });
        });
    }

    /**
     * A block pulled loose from a grid at an angle to the beam starts out with its centre outside the column it
     * was found in, often outside the beam. The beam has to take hold of it anyway, or it drops straight back.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void looseBlockBesideTheBeam_isTakenHoldOf(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        helper.runAfterDelay(FORM_TICKS + 2, () -> {
            // 0.7 to one side of the column's centre line and as much above it: clear of the 1x1 footprint both
            // ways, and clear of the floor, which it would otherwise land on before the beam's first tick with it.
            Vec3 beside = helper.absoluteVec(new Vec3(6.5, 2.7, 6.2));
            FallingBlockEntity loose = FallingBlockEntityInvoker.zps$create(
                    helper.getLevel(), beside.x, beside.y, beside.z, Blocks.COBBLESTONE.defaultBlockState());
            helper.getLevel().addFreshEntity(loose);
        });

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(count(beam.getInventory(), Items.COBBLESTONE) == 1,
                    "The block beside the beam should have been drawn in and collected");
            helper.succeed();
        });
    }

    /**
     * Something else saying a carried block is on the ground, as the ship mods' own collision does near a deck,
     * must not make it land: it would turn back into a block right where it was pulled loose.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void carriedBlock_doesNotLandEvenWhenToldItIsOnTheGround(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        helper.setBlock(BEAM.east(7), Blocks.DIRT);

        // Every tick of the flight, not once: whatever sets the flag sets it again.
        for (int tick = 2; tick <= 100; tick++) {
            helper.runAfterDelay(tick, () -> helper.getLevel()
                    .getEntitiesOfClass(FallingBlockEntity.class, helper.getBounds())
                    .forEach(falling -> falling.setOnGround(true)));
        }

        helper.runAfterDelay(110, () -> {
            for (int x = 2; x <= 8; x++) {
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 2, 5));
            }
            helper.assertTrue(count(beam.getInventory(), Items.DIRT) == 1, "The dirt should be carried all the way in");
            helper.succeed();
        });
    }

    /** Pulled straight down onto the mouth, a block must be swallowed rather than land on the panel. */
    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void upwardBeam_collectsInsteadOfLetting_blocksLand(GameTestHelper helper) {
        BlockPos up = new BlockPos(5, 1, 5);
        place(helper, up, Direction.UP);
        helper.setBlock(up.west(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(up.above(4), Blocks.DIRT);
        helper.runAfterDelay(FORM_TICKS, () -> fill(helper, up));

        helper.runAfterDelay(120, () -> {
            helper.assertBlockPresent(Blocks.AIR, up.above(4));
            helper.assertBlockPresent(Blocks.AIR, up.above(1));
            helper.assertTrue(count(beam(helper, up).getInventory(), Items.DIRT) == 1, "The dirt should be collected");
            helper.succeed();
        });
    }

    /** A suspicious block gives up what is buried in it as it is swallowed, then goes in as plain sand. */
    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void suspiciousSand_yieldsItsLootAndSand(GameTestHelper helper) {
        BeamCollectorBlockEntity beam = runningBeam(helper);
        BlockPos sand = BEAM.east(3);
        helper.setBlock(sand.below(), Blocks.STONE);
        helper.setBlock(sand, Blocks.SUSPICIOUS_SAND);
        BlockEntity buried = helper.getBlockEntity(sand);
        CompoundTag tag = buried.saveWithoutMetadata(helper.getLevel().registryAccess());
        tag.put("item", new ItemStack(Items.DIAMOND).save(helper.getLevel().registryAccess()));
        buried.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.runAfterDelay(120, () -> {
            helper.assertBlockPresent(Blocks.AIR, sand);
            helper.assertTrue(count(beam.getInventory(), Items.DIAMOND) == 1, "The buried diamond should be collected");
            helper.assertTrue(count(beam.getInventory(), Items.SAND) == 1, "The sand itself should be collected");
            helper.succeed();
        });
    }

    // --- living things ----------------------------------------------------------------------------

    /** A creature is drawn to the mouth and held in front of it: moved, not swallowed, not harmed. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void creature_isPulledToTheMouthAndHeld(GameTestHelper helper) {
        runningBeam(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, 7.5f, 2.0f, 5.5f);
        double startX = pig.getX();

        helper.runAfterDelay(150, () -> {
            double mouthX = helper.absolutePos(BEAM).getX() + 1.0;
            helper.assertTrue(pig.isAlive(), "The pig should survive the trip");
            helper.assertTrue(pig.getX() < startX - 2.0, "The pig should have been pulled toward the panel");
            helper.assertTrue(pig.getX() - mouthX < 3.0, "The pig should be held near the mouth, is "
                    + (pig.getX() - mouthX) + " blocks out");
            helper.succeed();
        });
    }

    /**
     * A player is weightless by the no-gravity flag for exactly as long as a beam has hold of it, and only a
     * player the beam made weightless is given its gravity back.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void player_isWeightlessOnlyWhileInTheBeam(GameTestHelper helper) {
        runningBeam(helper);
        Vec3 inside = helper.absoluteVec(new Vec3(5.5, 2.1, 5.5));
        Vec3 outside = helper.absoluteVec(new Vec3(5.5, 2.1, 9.5));

        // A few ticks, so the beam has signed in as running.
        helper.runAfterDelay(FORM_TICKS + 2, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(inside);
            PlayerWeightlessness.update(player);
            helper.assertTrue(player.isNoGravity(), "A player in the beam should be weightless");

            // Sneaking is no way out.
            player.setShiftKeyDown(true);
            PlayerWeightlessness.update(player);
            helper.assertTrue(player.isNoGravity(), "A sneaking player should be held like any other");
            player.setShiftKeyDown(false);

            player.getAbilities().flying = true;
            PlayerWeightlessness.update(player);
            helper.assertFalse(player.isNoGravity(), "Creative flight resists the beam, gravity included");
            player.getAbilities().flying = false;

            player.setPos(outside);
            PlayerWeightlessness.update(player);
            helper.assertFalse(player.isNoGravity(), "Gravity should return outside the beam");

            // Weightless for some other reason: not the beam's to undo.
            player.setNoGravity(true);
            player.setPos(inside);
            PlayerWeightlessness.update(player);
            player.setPos(outside);
            PlayerWeightlessness.update(player);
            helper.assertTrue(player.isNoGravity(), "The beam should not restore gravity it never took");
            helper.succeed();
        });
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static void place(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.BEAM_COLLECTOR.get().defaultBlockState().setValue(BeamCollectorBlock.FACING, facing));
    }

    /** A panel standing in the Y/Z plane (so facing east or west), {@code tall} by {@code wide}. */
    private static void placePanel(GameTestHelper helper, BlockPos corner, Direction facing, int tall, int wide) {
        for (int y = 0; y < tall; y++) {
            for (int z = 0; z < wide; z++) {
                place(helper, corner.offset(0, y, z), facing);
            }
        }
    }

    /** The lone east-facing beam, powered, and topped up as soon as it exists. */
    private static BeamCollectorBlockEntity runningBeam(GameTestHelper helper) {
        place(helper, BEAM, Direction.EAST);
        helper.setBlock(POWER, Blocks.REDSTONE_BLOCK);
        fill(helper, BEAM);
        // A full buffer runs a single block at full range for 128 ticks, and some tests watch for longer. Topped
        // up well clear of the first ticks, where the cost test takes its measurement.
        helper.runAfterDelay(100, () -> fill(helper, BEAM));
        return beam(helper, BEAM);
    }

    /**
     * A redstone signal of a chosen strength into the north side of {@code pos}: a comparator reading a composter
     * filled to that level, which is good for 0 to 8.
     */
    private static void powerWithStrength(GameTestHelper helper, BlockPos pos, int strength) {
        // A comparator's facing is the side it reads from; it puts out the other way.
        helper.setBlock(pos.north(), Blocks.COMPARATOR.defaultBlockState()
                .setValue(ComparatorBlock.FACING, Direction.NORTH));
        // The composter second: a comparator set down by code does nothing until what it reads changes.
        helper.setBlock(pos.north(2), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, strength));
    }

    private static void fill(GameTestHelper helper, BlockPos pos) {
        IEnergyStorage energy = beam(helper, pos).getEnergyStorage(null);
        if (energy == null) {
            helper.fail("The beam should expose an energy buffer");
            return;
        }
        energy.receiveEnergy(Integer.MAX_VALUE, false);
    }

    private static BeamCollectorBlockEntity beam(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockEntity(pos) instanceof BeamCollectorBlockEntity beam) {
            return beam;
        }
        helper.fail("Expected a beam collector at " + pos);
        throw new IllegalStateException();
    }

    private static int count(IItemHandler inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int countLoose(GameTestHelper helper, Item item) {
        AABB bounds = helper.getBounds().inflate(2.0);
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }
}
