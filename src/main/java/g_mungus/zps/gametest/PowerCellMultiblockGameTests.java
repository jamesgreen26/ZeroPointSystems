package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.PowerCellBlock;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.multiblock.ConnectivityHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class PowerCellMultiblockGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final BlockPos ORIGIN = new BlockPos(2, 1, 2);
    /** Placing a block calls for a connectivity update on the next tick; give it a couple. */
    private static final int FORM_TICKS = 3;

    /** A 2x2x2 box of cells forms one structure headed by the minimum corner, with pooled capacity. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void box_formsOneStructure(GameTestHelper helper) {
        placeBox(helper, ORIGIN, 2, 2);

        helper.runAfterDelay(FORM_TICKS, () -> {
            PowerCellBlockEntity controller = cell(helper, ORIGIN);
            helper.assertTrue(controller.isController(), "The minimum corner should be the controller");
            helper.assertTrue(controller.getWidth() == 2 && controller.getHeight() == 2,
                    "Expected a 2x2x2 structure, got " + controller.getWidth() + "x" + controller.getHeight());
            helper.assertTrue(controller.getMaxEnergyStored() == 8 * PowerCellBlockEntity.MAX_ENERGY,
                    "Capacity should pool across all 8 cells, got " + controller.getMaxEnergyStored());

            BlockPos far = ORIGIN.offset(1, 1, 1);
            PowerCellBlockEntity part = cell(helper, far);
            helper.assertFalse(part.isController(), "The far corner should not be a controller");
            helper.assertTrue(part.getController().equals(helper.absolutePos(ORIGIN)),
                    "Every part should point at the controller");
            helper.assertTrue(ConnectivityHandler.isConnected(helper.getLevel(), helper.absolutePos(ORIGIN),
                    helper.absolutePos(far)), "Parts should report as connected");

            BlockState farState = helper.getBlockState(far);
            helper.assertTrue(farState.getValue(PowerCellBlock.TOP) && !farState.getValue(PowerCellBlock.BOTTOM),
                    "The top layer should be flagged as top only");
            helper.assertTrue(farState.getValue(PowerCellBlock.EAST) && farState.getValue(PowerCellBlock.SOUTH)
                    && !farState.getValue(PowerCellBlock.WEST) && !farState.getValue(PowerCellBlock.NORTH),
                    "Only the outward sides of the far corner should be exposed");
            helper.succeed();
        });
    }

    /** Energy pushed into any part lands in the shared pool and can be read back from every part. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void energy_isSharedThroughEveryPart(GameTestHelper helper) {
        placeBox(helper, ORIGIN, 2, 1);

        helper.runAfterDelay(FORM_TICKS, () -> {
            BlockPos part = ORIGIN.offset(1, 0, 1);
            IEnergyStorage viaPart = energyAt(helper, part, Direction.UP);
            int accepted = viaPart.receiveEnergy(4 * PowerCellBlockEntity.MAX_TRANSFER, false);
            helper.assertTrue(accepted == PowerCellBlockEntity.MAX_TRANSFER,
                    "The transfer rate should not scale with structure size, accepted " + accepted);

            IEnergyStorage viaController = energyAt(helper, ORIGIN, Direction.DOWN);
            helper.assertTrue(viaController.getEnergyStored() == accepted,
                    "The controller should hold what was pushed into a part, has " + viaController.getEnergyStored());
            helper.assertTrue(cell(helper, part).getEnergyStored() == accepted,
                    "A part should report the pooled energy");
            helper.succeed();
        });
    }

    /** Breaking a part dissolves the structure and hands the pooled energy back out to the remaining cells. */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void break_splitsAndKeepsEnergy(GameTestHelper helper) {
        placeBox(helper, ORIGIN, 2, 1);
        int toStore = 3 * PowerCellBlockEntity.MAX_ENERGY;

        helper.runAfterDelay(FORM_TICKS, () -> {
            PowerCellBlockEntity controller = cell(helper, ORIGIN);
            int stored = 0;
            IEnergyStorage energy = controller.getEnergyStorage(null);
            while (stored < toStore) {
                int got = energy.receiveEnergy(toStore - stored, false);
                if (got <= 0) {
                    break;
                }
                stored += got;
            }
            helper.assertTrue(stored == toStore, "Should be able to fill 3 cells' worth, got " + stored);
            helper.setBlock(ORIGIN.offset(1, 0, 0), Blocks.AIR.defaultBlockState());
        });

        helper.runAfterDelay(FORM_TICKS + 3, () -> {
            long total = 0;
            for (BlockPos pos : new BlockPos[]{ORIGIN, ORIGIN.offset(0, 0, 1), ORIGIN.offset(1, 0, 1)}) {
                PowerCellBlockEntity cell = cell(helper, pos);
                helper.assertTrue(cell.isController() && cell.getWidth() == 1 && cell.getHeight() == 1,
                        "Every leftover cell should be standalone after the split, " + pos + " was not");
                helper.assertTrue(cell.getEnergyStored() <= PowerCellBlockEntity.MAX_ENERGY,
                        "A standalone cell must not exceed its own capacity");
                total += cell.getEnergyStored();
            }
            helper.assertTrue(total == toStore,
                    "Energy should be redistributed without loss, remaining " + total + " of " + toStore);
            helper.succeed();
        });
    }

    /** Two cells side by side are not a square footprint, so they stay separate. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void pair_staysSeparate(GameTestHelper helper) {
        helper.setBlock(ORIGIN, ModBlocks.POWER_CELL.get().defaultBlockState());
        helper.setBlock(ORIGIN.east(), ModBlocks.POWER_CELL.get().defaultBlockState());

        helper.runAfterDelay(FORM_TICKS, () -> {
            for (BlockPos pos : new BlockPos[]{ORIGIN, ORIGIN.east()}) {
                PowerCellBlockEntity cell = cell(helper, pos);
                helper.assertTrue(cell.isController() && cell.getWidth() == 1 && cell.getHeight() == 1,
                        "Two cells in a row should remain separate, " + pos + " did not");
                helper.assertTrue(cell.getMaxEnergyStored() == PowerCellBlockEntity.MAX_ENERGY,
                        "A lone cell keeps its own capacity");
            }
            helper.succeed();
        });
    }

    /** A column grows when another cell is placed on top, and the new top layer takes over the top plate. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void column_growsUpwards(GameTestHelper helper) {
        placeBox(helper, ORIGIN, 1, 2);

        helper.runAfterDelay(FORM_TICKS, () -> {
            helper.setBlock(ORIGIN.above(2), ModBlocks.POWER_CELL.get().defaultBlockState());
        });
        helper.runAfterDelay(2 * FORM_TICKS, () -> {
            PowerCellBlockEntity controller = cell(helper, ORIGIN);
            helper.assertTrue(controller.getHeight() == 3, "Expected a 3-tall column, got " + controller.getHeight());
            helper.assertTrue(helper.getBlockState(ORIGIN).getValue(PowerCellBlock.BOTTOM)
                            && !helper.getBlockState(ORIGIN).getValue(PowerCellBlock.TOP),
                    "The bottom cell should only carry the bottom plate");
            helper.assertTrue(!helper.getBlockState(ORIGIN.above()).getValue(PowerCellBlock.BOTTOM)
                            && !helper.getBlockState(ORIGIN.above()).getValue(PowerCellBlock.TOP),
                    "The middle cell should carry no plates");
            helper.assertTrue(helper.getBlockState(ORIGIN.above(2)).getValue(PowerCellBlock.TOP)
                            && !helper.getBlockState(ORIGIN.above(2)).getValue(PowerCellBlock.BOTTOM),
                    "The top cell should only carry the top plate");
            helper.succeed();
        });
    }

    // --- helpers ------------------------------------------------------------------------------------

    private static void placeBox(GameTestHelper helper, BlockPos origin, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    helper.setBlock(origin.offset(x, y, z), ModBlocks.POWER_CELL.get().defaultBlockState());
                }
            }
        }
    }

    private static PowerCellBlockEntity cell(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        if (!(blockEntity instanceof PowerCellBlockEntity cell)) {
            throw new GameTestAssertException("Expected a power cell at " + pos + ", found " + blockEntity);
        }
        return cell;
    }

    private static IEnergyStorage energyAt(GameTestHelper helper, BlockPos pos, Direction side) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(pos), blockEntity.getBlockState(), blockEntity, side);
        if (energy == null) {
            throw new GameTestAssertException("The power cell at " + pos + " should expose energy");
        }
        return energy;
    }
}
