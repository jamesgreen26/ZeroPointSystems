package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.commands.debug.armchain.ArmChain;
import g_mungus.zps.commands.debug.armchain.ArmChainKit;
import g_mungus.zps.commands.debug.armchain.Frame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The {@code zps:arm_chain_kit} template, placed at two heights of different parity: once its lever is pulled, the
 * loader feeds the chain and the chain builds the next two arms (trimmed to two kits to stay inside the test).
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmChainKitGameTests {

    private static final String TEMPLATE = "gametest/flat_11x9x11";
    private static final int KITS = 2;
    private static final int TIMEOUT = KITS * ArmChain.KIT_SIZE * (ArmChain.CHAIN_LINES + 2) * ArmChain.DELAY + 1200;

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void kitBuildsArmsOnTheFloor(GameTestHelper helper) {
        placeAndRun(helper, new BlockPos(0, 2, 1));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void kitBuildsArmsOneBlockHigher(GameTestHelper helper) {
        placeAndRun(helper, new BlockPos(0, 3, 1));
    }

    private static void placeAndRun(GameTestHelper helper, BlockPos corner) {
        StructureTemplate template = helper.getLevel().getStructureManager().get(ArmChainKit.TEMPLATE).orElseThrow();
        BlockPos at = helper.absolutePos(corner);
        template.placeInWorld(helper.getLevel(), at, at, new StructurePlaceSettings(), helper.getLevel().random, Block.UPDATE_ALL);

        Container supply = ArmChainKit.supply(helper.getLevel(), at.offset(ArmChainKit.CHEST));
        for (int slot = KITS * ArmChain.KIT_SIZE; slot < supply.getContainerSize(); slot++) {
            supply.setItem(slot, ItemStack.EMPTY);
        }
        helper.pullLever(corner.offset(ArmChainKit.LEVER));

        // Positions relative to the test, which is where the assertions want them.
        Frame frame = new Frame(corner.offset(ArmChainKit.FIRST_ARM), Direction.EAST, Direction.SOUTH);
        ArmChain chain = new ArmChain(frame);
        helper.succeedWhen(() -> {
            for (int k = 1; k <= KITS; k++) {
                BlockPos arm = chain.arm(k);
                helper.assertBlockPresent(ModBlocks.ROBOTIC_ARM.get(), arm);
                assertFacing(helper, arm.west(), ModBlocks.SERIAL_BUS.get(), Direction.EAST);
                assertFacing(helper, arm.east(), ModBlocks.STEPDOWN_TRANSFORMER.get(), Direction.WEST);
            }
        });
    }

    private static void assertFacing(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        helper.assertBlockPresent(block, pos);
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.getValue(TransformerBlock.FACING) == facing,
                "Expected " + block + " at " + pos + " facing " + facing + ", got " + state.getValue(TransformerBlock.FACING));
    }
}
