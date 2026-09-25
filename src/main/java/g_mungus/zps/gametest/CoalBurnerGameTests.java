package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.CoalBurnerBlockEntity;
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
public class CoalBurnerGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos BURNER_POS = new BlockPos(3, 1, 3);

    /** Modded/non-coal furnace fuels are accepted, not just the coals tag. */
    @GameTest(template = TEMPLATE)
    public static void automation_acceptsAnyFurnaceFuel(GameTestHelper helper) {
        CoalBurnerBlockEntity burner = placeBurner(helper);
        IItemHandler automation = burner.getItemHandler(null);

        ItemStack rejected = automation.insertItem(0, new ItemStack(Items.OAK_PLANKS, 8), false);
        if (!rejected.isEmpty()) {
            helper.fail("Planks should be accepted as fuel, got remainder " + rejected);
        }
        ItemStack notFuel = automation.insertItem(0, new ItemStack(Items.DIRT), true);
        if (notFuel.getCount() != 1) {
            helper.fail("Dirt should be rejected as fuel");
        }
        helper.succeed();
    }

    /** Like the furnace, automation must not pull burnable fuel back out. */
    @GameTest(template = TEMPLATE)
    public static void automation_cannotExtractFuel(GameTestHelper helper) {
        CoalBurnerBlockEntity burner = placeBurner(helper);
        IItemHandlerModifiable raw = (IItemHandlerModifiable) burner.getFuelInventory();
        raw.setStackInSlot(0, new ItemStack(Items.COAL, 4));

        ItemStack extracted = burner.getItemHandler(null).extractItem(0, 64, false);

        if (!extracted.isEmpty()) {
            helper.fail("Automation extracted " + extracted + " from the fuel slot; expected nothing");
        }
        if (raw.getStackInSlot(0).getCount() != 4) {
            helper.fail("Fuel slot changed after blocked extraction: " + raw.getStackInSlot(0));
        }
        helper.succeed();
    }

    /** Burnt-out leftovers (lava bucket -> bucket) can be pulled out by automation. */
    @GameTest(template = TEMPLATE)
    public static void automation_canExtractEmptyBucket(GameTestHelper helper) {
        CoalBurnerBlockEntity burner = placeBurner(helper);
        IItemHandlerModifiable raw = (IItemHandlerModifiable) burner.getFuelInventory();
        raw.setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));

        helper.runAfterDelay(2, () -> {
            if (!raw.getStackInSlot(0).is(Items.BUCKET)) {
                helper.fail("Expected an empty bucket left after burning lava, got " + raw.getStackInSlot(0));
                return;
            }
            ItemStack extracted = burner.getItemHandler(null).extractItem(0, 64, false);
            if (!extracted.is(Items.BUCKET) || !raw.getStackInSlot(0).isEmpty()) {
                helper.fail("Expected to extract the empty bucket, got " + extracted
                        + " (slot now " + raw.getStackInSlot(0) + ")");
                return;
            }
            helper.succeed();
        });
    }

    private static CoalBurnerBlockEntity placeBurner(GameTestHelper helper) {
        helper.setBlock(BURNER_POS, ModBlocks.COAL_BURNER.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(BURNER_POS));
        if (!(blockEntity instanceof CoalBurnerBlockEntity burner)) {
            throw new IllegalStateException("Expected coal burner at " + BURNER_POS + ", got " + blockEntity);
        }
        return burner;
    }
}
