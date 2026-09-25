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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
            if (!ItemStack.isSameItemSameComponents(stack, Items.COAL.getDefaultInstance())
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
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.useBlock(ARM_POS, player);

        assertArmEmpty(helper, arm);
        assertStack(helper, player.getItemInHand(InteractionHand.MAIN_HAND), new ItemStack(Items.COAL), TRANSFER_COUNT, "Player hand after taking");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerUse_matchingItem_topsUpHeldStack(GameTestHelper helper) {
        RoboticArmBlockEntity arm = placeLoadedArm(helper);
        if (arm == null) return;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        CompoundTag tag = coalBurner.saveWithFullMetadata(coalBurner.getLevel().registryAccess());
        int maxEnergy = coalBurner.getEnergyStorage(null).getMaxEnergyStored();
        tag.putInt("Energy", maxEnergy);
        coalBurner.loadWithComponents(tag, coalBurner.getLevel().registryAccess());
        coalBurner.setChanged();
    }

    private static void fillEnergy(RoboticArmBlockEntity arm) {
        var energy = arm.getEnergyStorage(null);
        energy.receiveEnergy(energy.getMaxEnergyStored(), false);
    }

    private static void assertStack(GameTestHelper helper, ItemStack actual, ItemStack expectedItem, int expectedCount, String context) {
        if (!ItemStack.isSameItemSameComponents(actual, expectedItem) || actual.getCount() != expectedCount) {
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
