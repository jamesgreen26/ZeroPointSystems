package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.RollingMillBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class RollingMillGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos MILL_POS = new BlockPos(3, 1, 3);
    private static final int SEED_COUNT = 4;

    /** Automation (hoppers/pipes) must not be able to pull the unprocessed ingredient back out. */
    @GameTest(template = TEMPLATE)
    public static void automation_cannotExtractFromInput(GameTestHelper helper) {
        RollingMillBlockEntity mill = placeMill(helper);
        IItemHandlerModifiable raw = rawInventory(mill);
        raw.setStackInSlot(RollingMillBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, SEED_COUNT));

        IItemHandler automation = mill.getItemHandler(null);
        ItemStack extracted = automation.extractItem(RollingMillBlockEntity.INPUT_SLOT, 64, false);

        if (!extracted.isEmpty()) {
            helper.fail("Automation extracted " + extracted + " from the input slot; expected nothing");
        }
        assertSlot(helper, raw, RollingMillBlockEntity.INPUT_SLOT, Items.IRON_INGOT.getDefaultInstance(), SEED_COUNT,
                "input slot after blocked extraction");
        helper.succeed();
    }

    /** Automation must still be able to pull finished results from the output slot. */
    @GameTest(template = TEMPLATE)
    public static void automation_canExtractFromOutput(GameTestHelper helper) {
        RollingMillBlockEntity mill = placeMill(helper);
        IItemHandlerModifiable raw = rawInventory(mill);
        raw.setStackInSlot(RollingMillBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_NUGGET, SEED_COUNT));

        IItemHandler automation = mill.getItemHandler(null);
        ItemStack extracted = automation.extractItem(RollingMillBlockEntity.OUTPUT_SLOT, 64, false);

        assertStack(helper, extracted, Items.IRON_NUGGET.getDefaultInstance(), SEED_COUNT, "output extraction result");
        if (!raw.getStackInSlot(RollingMillBlockEntity.OUTPUT_SLOT).isEmpty()) {
            helper.fail("Output slot should be empty after extraction, got "
                    + raw.getStackInSlot(RollingMillBlockEntity.OUTPUT_SLOT));
        }
        helper.succeed();
    }

    /** The GUI handler stays unrestricted so players can reclaim their ingredient from the input slot. */
    @GameTest(template = TEMPLATE)
    public static void menu_canExtractFromInput(GameTestHelper helper) {
        RollingMillBlockEntity mill = placeMill(helper);
        IItemHandlerModifiable raw = rawInventory(mill);
        raw.setStackInSlot(RollingMillBlockEntity.INPUT_SLOT, new ItemStack(Items.IRON_INGOT, SEED_COUNT));

        ItemStack extracted = mill.getMenuInventory().extractItem(RollingMillBlockEntity.INPUT_SLOT, 64, false);

        assertStack(helper, extracted, Items.IRON_INGOT.getDefaultInstance(), SEED_COUNT, "menu input extraction result");
        helper.succeed();
    }

    /** Automation must never be able to insert into the output slot. */
    @GameTest(template = TEMPLATE)
    public static void automation_cannotInsertIntoOutput(GameTestHelper helper) {
        RollingMillBlockEntity mill = placeMill(helper);

        IItemHandler automation = mill.getItemHandler(null);
        ItemStack toInsert = new ItemStack(Items.IRON_NUGGET, SEED_COUNT);
        ItemStack leftover = automation.insertItem(RollingMillBlockEntity.OUTPUT_SLOT, toInsert, false);

        if (leftover.getCount() != SEED_COUNT) {
            helper.fail("Output slot accepted insertion; expected " + SEED_COUNT + " rejected, got leftover " + leftover);
        }
        if (!rawInventory(mill).getStackInSlot(RollingMillBlockEntity.OUTPUT_SLOT).isEmpty()) {
            helper.fail("Output slot should remain empty after rejected insertion");
        }
        helper.succeed();
    }

    private static RollingMillBlockEntity placeMill(GameTestHelper helper) {
        helper.setBlock(MILL_POS, ModBlocks.ROLLING_MILL.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(MILL_POS));
        if (!(blockEntity instanceof RollingMillBlockEntity mill)) {
            helper.fail("Expected rolling mill at " + MILL_POS + ", got " + blockEntity);
            throw new IllegalStateException("unreachable");
        }
        return mill;
    }

    private static IItemHandlerModifiable rawInventory(RollingMillBlockEntity mill) {
        return (IItemHandlerModifiable) mill.getMenuInventory();
    }

    private static void assertSlot(GameTestHelper helper, IItemHandler handler, int slot, ItemStack expectedItem,
                                   int expectedCount, String context) {
        assertStack(helper, handler.getStackInSlot(slot), expectedItem, expectedCount, context);
    }

    private static void assertStack(GameTestHelper helper, ItemStack actual, ItemStack expectedItem, int expectedCount,
                                    String context) {
        if (!ItemStack.isSameItemSameTags(actual, expectedItem) || actual.getCount() != expectedCount) {
            helper.fail(context + ": expected " + expectedCount + "x " + expectedItem.getHoverName().getString()
                    + ", got " + actual.getCount() + "x " + actual.getHoverName().getString());
        }
    }
}
