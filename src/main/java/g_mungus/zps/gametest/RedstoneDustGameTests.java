package g_mungus.zps.gametest;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.core.Channels;
import g_mungus.zps.block.cableNetwork.light_pipe.DataComparator;
import g_mungus.zps.block.cableNetwork.light_pipe.SerialBusBlock;
import g_mungus.zps.blockentity.RedstoneConverterBlockEntity;
import g_mungus.zps.blockentity.light_pipe.SerialBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * What ZPS blocks put into redstone dust, and take back out of it. Dust is the one vanilla component that
 * wire-rewrite mods (Alternate Current and the like) replace wholesale, and they are stricter than vanilla
 * about what counts as a power source, so these hold with and without one installed.
 */
@GameTestHolder(ZPSMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class RedstoneDustGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static final BlockPos EMITTER = new BlockPos(3, 1, 4);
    private static final BlockPos DUST = new BlockPos(3, 1, 3);
    private static final BlockPos FAR_DUST = new BlockPos(3, 1, 2);
    private static final BlockPos LAMP = new BlockPos(3, 1, 1);

    /** A line of dust running north from the emitter's spot, on stone since the template's floor won't hold it. */
    private static void placeDust(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, Blocks.REDSTONE_WIRE.defaultBlockState()
                    .setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
                    .setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE));
        }
    }

    private static void assertDust(GameTestHelper helper, BlockPos pos, int expected) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.is(Blocks.REDSTONE_WIRE), "No dust at " + pos + ": " + state);
        int power = state.getValue(RedStoneWireBlock.POWER);
        helper.assertTrue(power == expected, "Dust at " + pos + " should be at " + expected + ", is at " + power);
    }

    private static RedstoneConverterBlockEntity placeConverter(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.REDSTONE_CONVERTER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, facing));
        if (!(helper.getBlockEntity(pos) instanceof RedstoneConverterBlockEntity converter)) {
            helper.fail("No converter at " + pos);
            throw new IllegalStateException();
        }
        return converter;
    }

    /** A converter given 15 by its network powers the dust it faces, and a lamp down the line. */
    @GameTest(template = TEMPLATE)
    public static void converterIntoDust_powersLine(GameTestHelper helper) {
        placeDust(helper, DUST, FAR_DUST);
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        RedstoneConverterBlockEntity converter = placeConverter(helper, EMITTER, Direction.NORTH);
        converter.receiveSignal(15, Channels.MAIN);
        helper.succeedWhen(() -> {
            assertDust(helper, DUST, 15);
            assertDust(helper, FAR_DUST, 14);
            helper.assertBlockProperty(LAMP, RedstoneLampBlock.LIT, true);
        });
    }

    /** Dust a converter was powering goes dark when the converter's signal does. */
    @GameTest(template = TEMPLATE)
    public static void converterIntoDust_releasesLine(GameTestHelper helper) {
        placeDust(helper, DUST, FAR_DUST);
        RedstoneConverterBlockEntity converter = placeConverter(helper, EMITTER, Direction.NORTH);
        converter.receiveSignal(15, Channels.MAIN);
        helper.runAfterDelay(5, () -> {
            assertDust(helper, DUST, 15);
            converter.receiveSignal(0, Channels.MAIN);
            helper.succeedWhen(() -> {
                assertDust(helper, DUST, 0);
                assertDust(helper, FAR_DUST, 0);
            });
        });
    }

    /** Intermediate strengths pass into dust as they are. */
    @GameTest(template = TEMPLATE)
    public static void converterIntoDust_keepsStrength(GameTestHelper helper) {
        placeDust(helper, DUST, FAR_DUST);
        RedstoneConverterBlockEntity converter = placeConverter(helper, EMITTER, Direction.NORTH);
        converter.receiveSignal(6, Channels.MAIN);
        helper.succeedWhen(() -> {
            assertDust(helper, DUST, 6);
            assertDust(helper, FAR_DUST, 5);
        });
    }

    /** A converter reading dust lets go when the dust loses its source. */
    @GameTest(template = TEMPLATE)
    public static void dustIntoConverter_releases(GameTestHelper helper) {
        placeDust(helper, DUST, FAR_DUST);
        RedstoneConverterBlockEntity converter = placeConverter(helper, EMITTER, Direction.NORTH);
        helper.setBlock(LAMP, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(5, () -> {
            int supplied = converter.getCurrentSuppliedSignal(Channels.MAIN);
            helper.assertTrue(supplied == 13, "Two dust from a redstone block should give 13, gives " + supplied);
            helper.setBlock(LAMP, Blocks.AIR);
            helper.succeedWhen(() -> {
                int after = converter.getCurrentSuppliedSignal(Channels.MAIN);
                helper.assertTrue(after == 0, "Unpowered dust should give the converter 0, gives " + after);
            });
        });
    }

    /** A Data Comparator that is on powers dust beside it. */
    @GameTest(template = TEMPLATE)
    public static void dataComparatorIntoDust_powersLine(GameTestHelper helper) {
        placeDust(helper, DUST, FAR_DUST);
        helper.setBlock(EMITTER, ModBlocks.DATA_COMPARATOR.get().defaultBlockState()
                .setValue(DataComparator.POWERED, true));
        helper.succeedWhen(() -> {
            assertDust(helper, DUST, 15);
            assertDust(helper, FAR_DUST, 14);
        });
    }

    /** A Serial Bus puts what {@code set_redstone} asked for into the dust in front of it, and takes it away. */
    @GameTest(template = TEMPLATE)
    public static void serialBusIntoDust_powersAndReleases(GameTestHelper helper) {
        placeDust(helper, DUST, FAR_DUST);
        helper.setBlock(EMITTER, ModBlocks.SERIAL_BUS.get().defaultBlockState()
                .setValue(SerialBusBlock.FACING, Direction.NORTH));
        if (!(helper.getBlockEntity(EMITTER) instanceof SerialBusBlockEntity bus)) {
            helper.fail("No serial bus at " + EMITTER);
            return;
        }
        bus.setRedstoneOutput(9);
        helper.runAfterDelay(5, () -> {
            assertDust(helper, DUST, 9);
            assertDust(helper, FAR_DUST, 8);
            bus.setRedstoneOutput(0);
            helper.succeedWhen(() -> {
                assertDust(helper, DUST, 0);
                assertDust(helper, FAR_DUST, 0);
            });
        });
    }
}
