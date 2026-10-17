package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.LargeSpaceDoorBlock;
import g_mungus.zps.block.LargeSpaceDoorBlock.Part;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.LargeSpaceDoorBlockEntity;
import g_mungus.zps.item.ModItems;
import g_mungus.zps.recipe.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LargeSpaceDoorGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos CONTROLLER = new BlockPos(3, 2, 3);

    /** Places the door the way a player standing to the north of it would, so it faces north. */
    private static boolean place(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.setYRot(0); // looking south
        BlockPos floor = helper.absolutePos(CONTROLLER.below());
        BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.LARGE_SPACE_DOOR.get()),
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
        return ((BlockItem) ModItems.LARGE_SPACE_DOOR.get()).place(context).consumesAction();
    }

    private static BlockPos part(GameTestHelper helper, Part part) {
        BlockState controller = helper.getBlockState(CONTROLLER);
        return LargeSpaceDoorBlock.partPos(CONTROLLER, controller, part);
    }

    private static void assertAllParts(GameTestHelper helper, java.util.function.Predicate<BlockState> test,
                                       String message) {
        for (Part part : Part.values()) {
            BlockPos pos = part(helper, part);
            helper.assertTrue(test.test(helper.getBlockState(pos)), message + " at " + part + ", got "
                    + helper.getBlockState(pos));
        }
    }

    /** Placing one item fills the whole 3x3, each block knowing its place. */
    @GameTest(template = TEMPLATE)
    public static void placesNineParts(GameTestHelper helper) {
        helper.assertTrue(place(helper), "The door should place");
        helper.assertBlockProperty(CONTROLLER, LargeSpaceDoorBlock.FACING, Direction.NORTH);
        for (Part part : Part.values()) {
            helper.assertBlockProperty(part(helper, part), LargeSpaceDoorBlock.PART, part);
        }
        // Seen from the north, the left column is to the east.
        helper.assertBlockProperty(CONTROLLER.east().above(2), LargeSpaceDoorBlock.PART, Part.TOP_LEFT);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void needsRoomToPlace(GameTestHelper helper) {
        helper.setBlock(CONTROLLER.west().above(2), Blocks.STONE);
        helper.assertFalse(place(helper), "The door should not place into a blocked doorway");
        helper.assertBlockNotPresent(ModBlocks.LARGE_SPACE_DOOR.get(), CONTROLLER);
        helper.succeed();
    }

    /** Using any part opens the whole door, and nothing collides while it is open. */
    @GameTest(template = TEMPLATE)
    public static void opensByHand(GameTestHelper helper) {
        place(helper);
        Player player = helper.makeMockSurvivalPlayer();
        helper.useBlock(part(helper, Part.TOP_LEFT), player);
        assertAllParts(helper, s -> s.getValue(LargeSpaceDoorBlock.OPEN), "Expected open");
        BlockPos center = helper.absolutePos(part(helper, Part.CENTER));
        helper.assertTrue(helper.getBlockState(part(helper, Part.CENTER))
                        .getCollisionShape(helper.getLevel(), center, CollisionContext.empty()).isEmpty(),
                "An open door should not collide");
        helper.useBlock(CONTROLLER, player);
        assertAllParts(helper, s -> !s.getValue(LargeSpaceDoorBlock.OPEN), "Expected closed");
        helper.succeed();
    }

    /** Open, each part can only be targeted where a slid-open half is still drawn in it. */
    @GameTest(template = TEMPLATE)
    public static void openShapesFollowTheHalves(GameTestHelper helper) {
        place(helper);
        helper.useBlock(CONTROLLER, helper.makeMockSurvivalPlayer());
        java.util.function.Function<Part, AABB> bounds = part -> {
            BlockPos pos = part(helper, part);
            var shape = helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos));
            return shape.isEmpty() ? null : shape.bounds();
        };
        // The middle column is clear once open.
        helper.assertTrue(bounds.apply(Part.CENTER) == null && bounds.apply(Part.TOP) == null
                && bounds.apply(Part.BOTTOM) == null, "The middle column should have no shape when open");
        // Facing north, the left column is to the east (+x). The left half's bottom reaches 31px across before
        // sliding 100/81 blocks (about 19.75px), leaving about 11.25px of the left column: x 4.75..16.
        double travel = LargeSpaceDoorBlock.OPEN_TRAVEL * 16;
        AABB bottomLeft = bounds.apply(Part.BOTTOM_LEFT);
        helper.assertTrue(bottomLeft != null && Math.abs(bottomLeft.minX - (16 - (31 - travel)) / 16) < 1e-6
                && bottomLeft.maxX == 1 && bottomLeft.minZ == 1 / 16.0 && bottomLeft.maxZ == 4 / 16.0,
                "Unexpected open bottom left shape " + bottomLeft);
        // The right half's top starts at 18px, so about 5.75px into the right column: x 0..10.25.
        AABB topRight = bounds.apply(Part.TOP_RIGHT);
        helper.assertTrue(topRight != null && topRight.minX == 0 && Math.abs(topRight.maxX - (16 - (18 + travel - 32)) / 16) < 1e-6,
                "Unexpected open top right shape " + topRight);
        helper.useBlock(part(helper, Part.TOP_RIGHT), helper.makeMockSurvivalPlayer());
        helper.assertTrue(bounds.apply(Part.CENTER) != null, "Closed, the whole door should be targetable");
        helper.succeed();
    }

    /** Power on any part holds the door open. */
    @GameTest(template = TEMPLATE)
    public static void opensWithRedstone(GameTestHelper helper) {
        place(helper);
        BlockPos beside = part(helper, Part.TOP_RIGHT).west();
        helper.setBlock(beside, Blocks.REDSTONE_BLOCK);
        assertAllParts(helper, LargeSpaceDoorBlock::isOpen, "Expected powered open");
        helper.setBlock(beside, Blocks.AIR);
        assertAllParts(helper, s -> !LargeSpaceDoorBlock.isOpen(s), "Expected closed once unpowered");
        helper.succeed();
    }

    /** Sneak-using with an empty hand locks it: hands no longer open it, redstone still does. */
    @GameTest(template = TEMPLATE)
    public static void lockedOpensOnlyWithRedstone(GameTestHelper helper) {
        place(helper);
        Player player = helper.makeMockSurvivalPlayer();
        player.setShiftKeyDown(true);
        helper.useBlock(CONTROLLER, player);
        assertAllParts(helper, s -> s.getValue(LargeSpaceDoorBlock.LOCKED), "Expected locked");
        player.setShiftKeyDown(false);
        helper.useBlock(CONTROLLER, player);
        assertAllParts(helper, s -> !LargeSpaceDoorBlock.isOpen(s), "A locked door should not open by hand");
        helper.setBlock(part(helper, Part.TOP_RIGHT).west(), Blocks.REDSTONE_BLOCK);
        assertAllParts(helper, LargeSpaceDoorBlock::isOpen, "A locked door should still open with redstone");
        helper.succeed();
    }

    /** Breaking any part takes down the whole door and gives back one. */
    @GameTest(template = TEMPLATE)
    public static void breaksWholeAndDropsOne(GameTestHelper helper) {
        place(helper);
        BlockPos top = part(helper, Part.TOP_RIGHT);
        helper.getLevel().destroyBlock(helper.absolutePos(top), true);
        for (Part part : Part.values()) {
            BlockPos pos = LargeSpaceDoorBlock.partPos(CONTROLLER, ModBlocks.LARGE_SPACE_DOOR.get().defaultBlockState(),
                    part);
            helper.assertBlockNotPresent(ModBlocks.LARGE_SPACE_DOOR.get(), pos);
        }
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(CONTROLLER)).inflate(4));
        int count = drops.stream().filter(e -> e.getItem().is(ModItems.LARGE_SPACE_DOOR.get()))
                .mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(count == 1, "Expected one large space door to drop, got " + count);
        helper.succeed();
    }

    /** The controller slides the halves fully open, then shut again. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void slidesOpenAndShut(GameTestHelper helper) {
        place(helper);
        Player player = helper.makeMockSurvivalPlayer();
        helper.useBlock(CONTROLLER, player);
        LargeSpaceDoorBlockEntity door = (LargeSpaceDoorBlockEntity) helper.getBlockEntity(CONTROLLER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(door.openness(1) == 1, "Not fully open: " + door.openness(1)))
                .thenExecute(() -> helper.useBlock(CONTROLLER, player))
                .thenWaitUntil(() -> helper.assertTrue(door.openness(1) == 0, "Not shut: " + door.openness(1)))
                .thenSucceed();
    }

    /** A 5x5 recipe: a row of space metal mesh through the middle of space metal ingots makes three. */
    @GameTest(template = TEMPLATE)
    public static void craftsFiveByFive(GameTestHelper helper) {
        List<ItemStack> grid = new java.util.ArrayList<>();
        for (int slot = 0; slot < 25; slot++) {
            grid.add(new ItemStack(slot / 5 == 2 ? ModItems.SPACE_METAL_MESH.get() : ModItems.SPACE_METAL_INGOT.get()));
        }
        net.minecraft.world.inventory.CraftingContainer input = new net.minecraft.world.inventory.TransientCraftingContainer(
                new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
                    @Override
                    public ItemStack quickMoveStack(Player player, int index) {
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }
                }, 5, 5);
        for (int slot = 0; slot < grid.size(); slot++) {
            input.setItem(slot, grid.get(slot));
        }
        ItemStack result = helper.getLevel().getRecipeManager()
                .getRecipeFor(ModRecipes.SHAPED_5X5_TYPE.get(), input, helper.getLevel())
                .map(holder -> holder.assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(ModItems.LARGE_SPACE_DOOR.get()) && result.getCount() == 3,
                "Expected 3x large space door, got " + result);
        helper.succeed();
    }
}
