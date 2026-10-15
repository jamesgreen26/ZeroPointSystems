package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.CoalBurnerBlockEntity;
import g_mungus.zps.blockentity.RoboticArmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class RoboticArmGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos ARM_POS = new BlockPos(3, 1, 3);
    private static final BlockPos TARGET_POS = new BlockPos(3, 1, 4);
    private static final int TRANSFER_COUNT = 3;

    @GameTest(template = TEMPLATE)
    public static void depositItems_vanillaChest_usesContainerTransfer(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        helper.setBlock(TARGET_POS, Blocks.CHEST);

        BlockEntity targetBlockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(TARGET_POS));
        if (!(targetBlockEntity instanceof Container chest)) {
            helper.fail("Expected chest container at " + TARGET_POS + ", got " + targetBlockEntity);
            return;
        }

        startDeposit(helper, arm);

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            ItemStack stack = chest.getItem(0);
            assertStack(helper, stack, Items.COAL.getDefaultInstance(), TRANSFER_COUNT, "chest slot");
            assertArmEmpty(helper, arm);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void depositItems_coalBurner_usesItemHandlerTransfer(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        helper.setBlock(TARGET_POS, ModBlocks.COAL_BURNER.get());

        BlockEntity targetBlockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(TARGET_POS));
        if (!(targetBlockEntity instanceof CoalBurnerBlockEntity coalBurner)) {
            helper.fail("Expected coal burner at " + TARGET_POS + ", got " + targetBlockEntity);
            return;
        }
        fillEnergy(coalBurner);

        startDeposit(helper, arm);

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            ItemStack stack = coalBurner.getFuelInventory().getStackInSlot(0);
            // The arm consumes energy while depositing, so the (full) coal burner may burn a single fuel
            // to top the neighbouring arm back up via its FE-push behaviour. The transfer itself must still
            // have delivered every coal, so allow for at most one fuel to have been consumed.
            if (!ItemStack.isSameItemSameTags(stack, Items.COAL.getDefaultInstance())
                    || stack.getCount() < TRANSFER_COUNT - 1 || stack.getCount() > TRANSFER_COUNT) {
                helper.fail("coal burner fuel slot: expected " + (TRANSFER_COUNT - 1) + "-" + TRANSFER_COUNT
                        + "x Coal, got " + stack.getCount() + "x " + stack.getHoverName().getString());
            }
            assertArmEmpty(helper, arm);
            helper.succeed();
        });
    }

    /**
     * A dirt-filled composter has no block entity, so its item handler comes from the bare block.
     * The arm takes the one dirt and leaves an empty composter, as a hopper would.
     */
    @GameTest(template = TEMPLATE)
    public static void takeItems_dirtComposter_takesTheDirt(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.getHeldStackAccess().setItem(0, ItemStack.EMPTY);
        helper.setBlock(TARGET_POS, ModBlocks.COMPOSTER_DIRT.get());

        if (!arm.RetrieveItemsFrom(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start retrieve");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            assertStack(helper, arm.getHeldStack(), Items.DIRT.getDefaultInstance(), 1, "arm held stack");
            if (helper.getBlockState(TARGET_POS) != Blocks.COMPOSTER.defaultBlockState()) {
                helper.fail("Expected an empty composter once the dirt was taken, got " + helper.getBlockState(TARGET_POS), TARGET_POS);
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void playerUse_emptyHand_takesHeldStack(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        Player player = helper.makeMockSurvivalPlayer();

        helper.useBlock(ARM_POS, player);

        assertArmEmpty(helper, arm);
        assertStack(helper, player.getItemInHand(InteractionHand.MAIN_HAND), new ItemStack(Items.COAL), TRANSFER_COUNT, "Player hand after taking");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerUse_matchingItem_topsUpHeldStack(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 64));

        helper.useBlock(ARM_POS, player);

        assertStack(helper, arm.getHeldStack(), new ItemStack(Items.COAL), 64, "Arm after top-up");
        assertStack(helper, player.getItemInHand(InteractionHand.MAIN_HAND), new ItemStack(Items.COAL), TRANSFER_COUNT, "Player hand after top-up");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerUse_emptyArm_acceptsWholeStack(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.clearContent();
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 10));

        helper.useBlock(ARM_POS, player);

        assertStack(helper, arm.getHeldStack(), new ItemStack(Items.IRON_INGOT), 10, "Arm after giving");
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            helper.fail("Expected player hand to be empty, got " + player.getItemInHand(InteractionHand.MAIN_HAND));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerUse_mismatchedItem_isRejected(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 10));

        helper.useBlock(ARM_POS, player);

        assertStack(helper, arm.getHeldStack(), new ItemStack(Items.COAL), TRANSFER_COUNT, "Arm after rejected give");
        assertStack(helper, player.getItemInHand(InteractionHand.MAIN_HAND), new ItemStack(Items.IRON_INGOT), 10, "Player hand after rejected give");
        helper.succeed();
    }

    /**
     * Filling one bucket from a stack leaves the filled bucket with nowhere to go but the fake
     * player's inventory. The arm keeps the rest of the stack and drops the filled bucket.
     */
    @GameTest(template = TEMPLATE)
    public static void use_bucketStackOnWaterCauldron_dropsFilledBucket(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.getHeldStackAccess().setItem(0, new ItemStack(Items.BUCKET, 16));
        helper.setBlock(TARGET_POS, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));

        if (!arm.UseAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start use");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            if (!helper.getBlockState(TARGET_POS).is(Blocks.CAULDRON)) {
                helper.fail("Expected the cauldron to be emptied, got " + helper.getBlockState(TARGET_POS), TARGET_POS);
            }
            assertStack(helper, arm.getHeldStack(), Items.BUCKET.getDefaultInstance(), 15, "arm held stack");
            helper.assertItemEntityCountIs(Items.WATER_BUCKET, TARGET_POS, 2.0D, 1);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void breakBlock_emptyHand_holdsTheDrop(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.takeHeldStack();
        helper.setBlock(TARGET_POS, Blocks.STONE);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start breaking stone");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
            assertStack(helper, arm.getHeldStack(), Items.COBBLESTONE.getDefaultInstance(), 1, "arm held stack");
            helper.assertItemEntityCountIs(Items.COBBLESTONE, TARGET_POS, 2.0D, 0);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void breakBlock_matchingHeldStack_topsItUp(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.getHeldStackAccess().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        helper.setBlock(TARGET_POS, Blocks.STONE);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start breaking stone");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            assertStack(helper, arm.getHeldStack(), Items.COBBLESTONE.getDefaultInstance(), 6, "arm held stack");
            helper.succeed();
        });
    }

    /** The arm holds coal, so the raw iron has nowhere to go but the world. */
    @GameTest(template = TEMPLATE)
    public static void breakBlock_otherHeldItem_dropsTheDrop(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        helper.setBlock(TARGET_POS, Blocks.IRON_ORE);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start breaking iron ore");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
            assertStack(helper, arm.getHeldStack(), Items.COAL.getDefaultInstance(), TRANSFER_COUNT, "arm held stack");
            helper.assertItemEntityCountIs(Items.RAW_IRON, TARGET_POS, 2.0D, 1);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void breakBlock_fullHeldStack_dropsTheDrop(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.getHeldStackAccess().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.setBlock(TARGET_POS, Blocks.STONE);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start breaking stone");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            assertStack(helper, arm.getHeldStack(), Items.COBBLESTONE.getDefaultInstance(), 64, "arm held stack");
            helper.assertItemEntityCountIs(Items.COBBLESTONE, TARGET_POS, 2.0D, 1);
            helper.succeed();
        });
    }

    /** Silk touch keeps stone as stone. */
    @GameTest(template = TEMPLATE)
    public static void silkBreak_emptyHand_holdsTheBlock(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.takeHeldStack();
        helper.setBlock(TARGET_POS, Blocks.STONE);

        if (!arm.SilkBreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start silk breaking stone");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
            assertStack(helper, arm.getHeldStack(), Items.STONE.getDefaultInstance(), 1, "arm held stack");
            helper.succeed();
        });
    }

    /** Glass drops nothing to a plain break, and itself to a silk one. */
    @GameTest(template = TEMPLATE)
    public static void silkBreak_glass_dropsOnlyWithSilk(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.takeHeldStack();
        helper.setBlock(TARGET_POS, Blocks.GLASS);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start breaking glass");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
            if (!arm.getHeldStack().isEmpty()) {
                helper.fail("Expected nothing from a plain glass break, got " + arm.getHeldStack());
            }
            helper.setBlock(TARGET_POS, Blocks.GLASS);
            if (!arm.SilkBreakBlockAt(helper.absolutePos(TARGET_POS))) {
                helper.fail("Robotic arm failed to start silk breaking glass");
            }
            helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
                helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
                assertStack(helper, arm.getHeldStack(), Items.GLASS.getDefaultInstance(), 1, "arm held stack");
                helper.succeed();
            });
        });
    }

    /** Silk touch doesn't make an iron pickaxe mine obsidian. */
    @GameTest(template = TEMPLATE)
    public static void silkBreak_tooHardForIronPickaxe_isLeft(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.takeHeldStack();
        helper.setBlock(TARGET_POS, Blocks.OBSIDIAN);
        if (!arm.SilkBreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm refused to move to the obsidian");
        }
        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.OBSIDIAN, TARGET_POS);
            if (!arm.getHeldStack().isEmpty()) {
                helper.fail("Expected an empty hand, got " + arm.getHeldStack());
            }
            helper.succeed();
        });
    }

    /** Obsidian needs a diamond pickaxe, so the hand goes there, as for any command, but leaves it. */
    @GameTest(template = TEMPLATE)
    public static void breakBlock_tooHardForIronPickaxe_isLeft(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        arm.takeHeldStack();
        helper.setBlock(TARGET_POS, Blocks.OBSIDIAN);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm refused to move to the obsidian");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.OBSIDIAN, TARGET_POS);
            if (!arm.getHandBlockPos().equals(helper.absolutePos(TARGET_POS))) {
                helper.fail("Expected the hand at the obsidian, got " + arm.getHandBlockPos());
            }
            if (!arm.getHeldStack().isEmpty()) {
                helper.fail("Expected an empty hand, got " + arm.getHeldStack());
            }
            helper.succeed();
        });
    }

    /** Bedrock can't be broken at all; the hand still goes there. */
    @GameTest(template = TEMPLATE)
    public static void breakBlock_bedrock_isLeft(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        helper.setBlock(TARGET_POS, Blocks.BEDROCK);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm refused to move to the bedrock");
        }

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.BEDROCK, TARGET_POS);
            if (!arm.getHandBlockPos().equals(helper.absolutePos(TARGET_POS))) {
                helper.fail("Expected the hand at the bedrock, got " + arm.getHandBlockPos());
            }
            helper.succeed();
        });
    }

    /** The block is checked again when the hand arrives, in case it changed on the way. */
    @GameTest(template = TEMPLATE)
    public static void breakBlock_blockHardenedDuringMove_isLeft(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        helper.setBlock(TARGET_POS, Blocks.STONE);

        if (!arm.BreakBlockAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start breaking stone");
        }
        helper.setBlock(TARGET_POS, Blocks.OBSIDIAN);

        helper.runAfterDelay(RoboticArmBlockEntity.MOVE_TIME_TICKS + 1, () -> {
            helper.assertBlockPresent(Blocks.OBSIDIAN, TARGET_POS);
            helper.succeed();
        });
    }

    private static RoboticArmBlockEntity placeLoadedArm(GameTestHelper helper) {
        helper.setBlock(ARM_POS, ModBlocks.ROBOTIC_ARM.get());

        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(ARM_POS));
        if (!(blockEntity instanceof RoboticArmBlockEntity arm)) {
            helper.fail("Expected robotic arm at " + ARM_POS + ", got " + blockEntity);
            return null;
        }

        fillEnergy(arm);
        arm.setArmSettings(TRANSFER_COUNT);
        arm.getHeldStackAccess().setItem(0, new ItemStack(Items.COAL, TRANSFER_COUNT));
        return arm;
    }

    private static void startDeposit(GameTestHelper helper, RoboticArmBlockEntity arm) {
        if (arm == null) return;
        if (!arm.DepositItemsAt(helper.absolutePos(TARGET_POS))) {
            helper.fail("Robotic arm failed to start deposit");
        }
    }

    private static void fillEnergy(CoalBurnerBlockEntity coalBurner) {
        CompoundTag tag = coalBurner.saveWithFullMetadata();
        int maxEnergy = coalBurner.getCapability(ForgeCapabilities.ENERGY)
                .map(IEnergyStorage::getMaxEnergyStored)
                .orElse(8192);
        tag.putInt("Energy", maxEnergy);
        coalBurner.load(tag);
        coalBurner.setChanged();
    }

    private static void fillEnergy(RoboticArmBlockEntity arm) {
        arm.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy ->
                energy.receiveEnergy(energy.getMaxEnergyStored(), false));
    }

    private static void assertStack(GameTestHelper helper, ItemStack actual, ItemStack expectedItem, int expectedCount, String context) {
        if (!ItemStack.isSameItemSameTags(actual, expectedItem) || actual.getCount() != expectedCount) {
            helper.fail(context + ": expected " + expectedCount + "x " + expectedItem.getHoverName().getString()
                    + ", got " + actual.getCount() + "x " + actual.getHoverName().getString());
        }
    }

    private static void assertArmEmpty(GameTestHelper helper, RoboticArmBlockEntity arm) {
        if (!arm.getHeldStack().isEmpty()) {
            helper.fail("Expected robotic arm held stack to be empty, got " + arm.getHeldStack());
        }
    }
}
