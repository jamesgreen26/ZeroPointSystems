package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpaceDoorGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos LOWER = new BlockPos(3, 1, 3);
    private static final BlockPos UPPER = LOWER.above();

    private static void placeDoor(GameTestHelper helper) {
        BlockState door = ModBlocks.SPACE_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.setBlock(LOWER, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(UPPER, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Like a wooden door, a player opens and closes it by hand, both halves together. */
    @GameTest(template = TEMPLATE)
    public static void opensByHand(GameTestHelper helper) {
        placeDoor(helper);
        Player player = helper.makeMockSurvivalPlayer();
        helper.useBlock(UPPER, player);
        helper.assertBlockProperty(LOWER, DoorBlock.OPEN, true);
        helper.assertBlockProperty(UPPER, DoorBlock.OPEN, true);
        helper.useBlock(LOWER, player);
        helper.assertBlockProperty(LOWER, DoorBlock.OPEN, false);
        helper.assertBlockProperty(UPPER, DoorBlock.OPEN, false);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void opensWithRedstone(GameTestHelper helper) {
        placeDoor(helper);
        helper.setBlock(LOWER.east(), Blocks.REDSTONE_BLOCK);
        helper.assertBlockProperty(LOWER, DoorBlock.OPEN, true);
        helper.assertBlockProperty(UPPER, DoorBlock.OPEN, true);
        helper.setBlock(LOWER.east(), Blocks.AIR);
        helper.assertBlockProperty(LOWER, DoorBlock.OPEN, false);
        helper.succeed();
    }

    /** Only the lower half drops, so a door broken from either half gives back one door. */
    @GameTest(template = TEMPLATE)
    public static void dropsOneDoor(GameTestHelper helper) {
        placeDoor(helper);
        ItemStack pickaxe = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        List<ItemStack> lower = Block.getDrops(helper.getBlockState(LOWER), helper.getLevel(),
                helper.absolutePos(LOWER), null, null, pickaxe);
        List<ItemStack> upper = Block.getDrops(helper.getBlockState(UPPER), helper.getLevel(),
                helper.absolutePos(UPPER), null, null, pickaxe);
        helper.assertTrue(lower.size() == 1 && lower.get(0).is(ModItems.SPACE_DOOR.get()),
                "Expected the lower half to drop a space door, got " + lower);
        helper.assertTrue(upper.isEmpty(), "Expected the upper half to drop nothing, got " + upper);
        helper.assertTrue(helper.getBlockState(LOWER).is(BlockTags.DOORS), "The space door should be in #doors");
        helper.succeed();
    }

    /** Six space metal ingots in two columns make three, like an iron door. */
    @GameTest(template = TEMPLATE)
    public static void craftsFromIngots(GameTestHelper helper) {
        CraftingContainer grid = new TransientCraftingContainer(new NoMenu(), 2, 3);
        ItemStack ingot = new ItemStack(ModItems.SPACE_METAL_INGOT.get());
        for (int slot = 0; slot < 6; slot++) {
            grid.setItem(slot, ingot.copy());
        }
        ItemStack result = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
                .map(holder -> holder.assemble(grid, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(ModItems.SPACE_DOOR.get()) && result.getCount() == 3,
                "Expected 3x space door, got " + result);
        helper.succeed();
    }

    private static final class NoMenu extends AbstractContainerMenu {
        NoMenu() {
            super((MenuType<?>) null, -1);
        }

        @Override
        public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return true;
        }
    }
}
