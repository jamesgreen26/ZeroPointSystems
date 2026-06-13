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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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
            assertStack(helper, stack, Items.COAL.getDefaultInstance(), TRANSFER_COUNT, "coal burner fuel slot");
            assertArmEmpty(helper, arm);
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
        arm.setArmSettings(TRANSFER_COUNT, false);
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
