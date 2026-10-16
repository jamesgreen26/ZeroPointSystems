package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.SpaceMetalLadderBlock;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpaceMetalLadderGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos LOWER = new BlockPos(3, 1, 3);
    private static final BlockPos UPPER = LOWER.above();

    private static BlockState ladder() {
        return ModBlocks.SPACE_METAL_LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
    }

    /** Only the top ladder of a run has hand holds, and the one below gets them back when it is taken away. */
    @GameTest(template = TEMPLATE)
    public static void handHoldsFollowTheTopOfTheRun(GameTestHelper helper) {
        helper.setBlock(LOWER.south(), Blocks.STONE);
        helper.setBlock(UPPER.south(), Blocks.STONE);
        helper.setBlock(LOWER, ladder());
        helper.setBlock(UPPER, ladder());
        helper.assertBlockProperty(LOWER, SpaceMetalLadderBlock.TOP, false);
        helper.assertBlockProperty(UPPER, SpaceMetalLadderBlock.TOP, true);

        helper.setBlock(UPPER, Blocks.AIR);
        helper.assertBlockProperty(LOWER, SpaceMetalLadderBlock.TOP, true);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void isClimbable(GameTestHelper helper) {
        helper.assertTrue(ladder().is(BlockTags.CLIMBABLE), "The space metal ladder should be climbable");
        helper.succeed();
    }

    /** Mined with a pickaxe, the ladder drops itself. */
    @GameTest(template = TEMPLATE)
    public static void dropsItself(GameTestHelper helper) {
        helper.setBlock(LOWER.south(), Blocks.STONE);
        helper.setBlock(LOWER, ladder());
        List<ItemStack> drops = Block.getDrops(helper.getBlockState(LOWER), helper.getLevel(),
                helper.absolutePos(LOWER), null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ModItems.SPACE_METAL_LADDER.get())
                && drops.get(0).getCount() == 1, "Expected 1x space metal ladder, got " + drops);
        helper.succeed();
    }

    /** The vanilla ladder shape with space metal rods for sticks makes six. */
    @GameTest(template = TEMPLATE)
    public static void craftsFromRods(GameTestHelper helper) {
        CraftingContainer grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        ItemStack rod = new ItemStack(ModItems.SPACE_METAL_ROD.get());
        for (int slot : new int[]{0, 2, 3, 4, 5, 6, 8}) {
            grid.setItem(slot, rod.copy());
        }
        ItemStack result = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), helper.getLevel())
                .map(holder -> holder.value().assemble(grid.asCraftInput(), helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(ModItems.SPACE_METAL_LADDER.get()) && result.getCount() == 6,
                "Expected 6x space metal ladder, got " + result);
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
