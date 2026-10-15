package g_mungus.zps.commands.debug.armchain;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A chain of Robotic Arms that builds itself in a straight 3x3 line, working as a bucket brigade: every arm takes a
 * stack from the barrel beside it and either places it, while it is the arm at the front building the next one, or
 * passes it on to the barrel of the arm after it. One script line runs every arm, every line: what each arm does is
 * chosen by what it holds and by the mode it keeps in its transfer count, so no arm waits out lines meant for
 * another stack, and only the loader's pacing, a stack as often as the front arm can use one, drives the build.
 * <p>
 * The arms step up and down between the floor and the level above it, two blocks apart, so that each new arm can
 * be given a Serial Bus behind it and a Step-Down Transformer in front, both facing it. In the {@link Frame} of the
 * chain, forward is the way it grows, side +1 is the power lane (power cables and barrels) and side -1 the data
 * lane. With {@code s} = +1 for an arm on the floor and -1 for one on the level
 * above, an arm places, for the next one:
 * <ul>
 *     <li>the arm at (2, s, 0), a Serial Bus at (1, s, 0) with shift-use so it faces forward onto it, and a
 *     Step-Down at (3, s, 0), which faces back onto it;</li>
 *     <li>data cables at (1..3, s, -1) and power cables at (1..3, s, +1), the cell picked by how many are left;</li>
 *     <li>the next arm's barrel at (2, s + 1, +1).</li>
 * </ul>
 * The first arm and the blocks the arm before it would have placed (the dock) are built by hand.
 */
public final class ArmChain {
    /** Ticks between script lines: an arm accepts a new move every 16 ticks. */
    public static final int DELAY = 16;
    /** Stacks in one kit. */
    public static final int KIT_SIZE = 6;
    /** Lines the front arm spends on a kit ({@link #frontLines} summed), and so how often the loader sends one. */
    public static final int KIT_LINES = 18;

    /*
     * An arm's mode, kept in its transfer count, which every take and put reads too; no stack is over 3, so all
     * of them move whole stacks. A new arm starts at 1.
     */
    private static final int NEW = 1;
    /** At the front: places what it takes. */
    private static final int BUILDING = 64;
    /** Has the next arm's barrel in hand, the last thing it builds. */
    private static final int PLACING_BARREL = 63;
    /** Its next arm is built: passes everything on. */
    private static final int PASSING = 62;

    private final Frame frame;

    /** @param frame origin at the first arm, on the floor; forward the way the chain grows; side the power lane */
    public ArmChain(Frame frame) {
        this.frame = frame;
    }

    public Frame frame() {
        return frame;
    }

    /** Where arm {@code k} stands, the first being 0. */
    public BlockPos arm(int k) {
        return frame.at(2 * k, k % 2, 0);
    }

    /** The barrel the first arm takes from, which the loader fills. */
    public BlockPos firstBarrel() {
        return frame.at(0, 1, 1);
    }

    // --- items --------------------------------------------------------------------------------------------------

    /** The stacks an arm needs to build the arm after it, in the order it must get them. */
    public static List<ItemStack> kit() {
        return List.of(
                new ItemStack(ModItems.DATA_CABLE.get(), 3),
                new ItemStack(ModItems.CABLE.get(), 3),
                new ItemStack(ModItems.SERIAL_BUS.get()),
                new ItemStack(ModItems.STEPDOWN_TRANSFORMER.get()),
                new ItemStack(ModItems.ROBOTIC_ARM.get()),
                // Last: placing the next barrel is what turns an arm from building to passing.
                new ItemStack(Items.BARREL));
    }

    /** Kits for {@code arms} arms after the first, in the order the loader feeds them. */
    public static List<ItemStack> kits(int arms) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < arms; i++) {
            stacks.addAll(kit());
        }
        return stacks;
    }

    /**
     * Lines the front arm spends on {@code stack}, from taking it to being free to take the next: one to take it
     * and one a block placed, and for the barrel one more each side of placing it, to change mode.
     */
    public static int frontLines(ItemStack stack) {
        return 1 + stack.getCount() + (stack.is(Items.BARREL) ? 2 : 0);
    }

    // --- scripts ------------------------------------------------------------------------------------------------

    /**
     * The script every arm of the chain runs, on {@link #DELAY} ticks a line, looping, from a terminal
     * {@code floorAboveTerminal} blocks below the level the first arm stands on (negative if the terminal is
     * higher). It is one line, which every arm runs every time:
     * <ul>
     *     <li>a new arm sets its mode to building;</li>
     *     <li>an empty hand takes from the arm's barrel;</li>
     *     <li>a passing arm puts what it holds in the next barrel;</li>
     *     <li>a building arm places what it holds, cables by how many it has left, and on getting the barrel
     *     changes to placing it, then to passing once it is down.</li>
     * </ul>
     * A stack so goes through a passing arm in two lines, and the front arm places a block a line. Every position is
     * relative to the arm running the line. Whether that arm is on the floor or the level above is told from the
     * parity of its height plus the terminal's ({@code pos offset ~ ~ ~ y}), the same as the parity of the
     * difference, and lifts each target by 2 on the floor and none above, so the script works wherever the chain is
     * built.
     */
    public String chainScript(int floorAboveTerminal) {
        // 2 on the floor, 0 on the level above.
        String lift = " offset_y value_of(pos offset ~ ~ ~ y + " + ((floorAboveTerminal + 1) & 1) + " & 1 << 1)";
        String empty = "held_item count == 0";
        List<String> branches = new ArrayList<>();
        branches.add("transfer_count == " + NEW + " set_transfer_count " + BUILDING);
        branches.add(empty + " && value_of(transfer_count == " + PLACING_BARREL + ") set_transfer_count " + PASSING);
        branches.add(empty + " take_items value_of(" + at(0, 1, 1) + ")");
        branches.add("transfer_count == " + PASSING + " put_items value_of(" + at(2, 0, 1) + lift + ")");
        branches.add(held(Items.BARREL) + " && value_of(transfer_count == " + BUILDING + ") set_transfer_count "
                + PLACING_BARREL);
        branches.add(held(Items.BARREL) + " use value_of(" + at(2, 0, 1) + lift + ")");
        for (int left = 3; left >= 1; left--) {
            int forward = 4 - left;
            branches.add(held(ModItems.DATA_CABLE.get()) + " && value_of(held_item count == " + left + ") use value_of("
                    + at(forward, -1, -1) + lift + ")");
            branches.add(held(ModItems.CABLE.get()) + " && value_of(held_item count == " + left + ") use value_of("
                    + at(forward, -1, 1) + lift + ")");
        }
        branches.add(held(ModItems.SERIAL_BUS.get()) + " shift_use value_of(" + at(1, -1, 0) + lift + ")");
        branches.add(held(ModItems.STEPDOWN_TRANSFORMER.get()) + " use value_of(" + at(3, -1, 0) + lift + ")");
        branches.add(held(ModItems.ROBOTIC_ARM.get()) + " use value_of(" + at(2, -1, 0) + lift + ")");
        return "if " + String.join(" else if ", branches);
    }

    /**
     * The loader's script, looping: a kit a pass from the supply chest into the first arm's barrel, a stack at a
     * time, each {@link #frontLines} after the one before, so the front arm is ready for every stack as it comes and
     * no barrel ever holds two. A take starts at the chest's first full slot, so the chest holds the kits slot by slot
     * in this order; the transfer count is set to each stack's size where it changes, because a take also gathers
     * matching items from later slots. Once the chest is empty the takes come up empty.
     *
     * @param chest       the supply chest, relative to the loader's terminal
     * @param firstBarrel the first arm's barrel, relative to the loader's terminal
     */
    public static String loaderScript(BlockPos chest, BlockPos firstBarrel) {
        List<ItemStack> kit = kit();
        if (kit.stream().mapToInt(ArmChain::frontLines).sum() != KIT_LINES) {
            throw new IllegalStateException("The front arm's lines for a kit are not " + KIT_LINES);
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < kit.size(); i++) {
            ItemStack stack = kit.get(i);
            int count = stack.getCount();
            boolean setCount = count != kit.get((i + kit.size() - 1) % kit.size()).getCount();
            if (setCount) {
                lines.add("set_transfer_count " + count);
            }
            lines.add("take_items " + relative(chest));
            lines.add("put_items " + relative(firstBarrel));
            // The next stack's put comes when the front arm is free for it.
            ItemStack next = kit.get((i + 1) % kit.size());
            int nextSetsCount = next.getCount() != count ? 1 : 0;
            int wait = frontLines(stack) - 2 - nextSetsCount;
            if (wait < 0) {
                throw new IllegalStateException("The loader cannot keep up with the front arm after " + stack);
            }
            if (wait > 0) {
                lines.add("wait " + wait);
            }
        }
        return String.join("\n", lines);
    }

    private static String held(Item item) {
        return "held_item == " + BuiltInRegistries.ITEM.getKey(item);
    }

    /** {@code pos offset} to the cell at (forward, up, side) from the arm running the line. */
    private String at(int forward, int up, int side) {
        BlockPos o = frame.offset(forward, up, side);
        return "pos offset " + o.getX() + " " + o.getY() + " " + o.getZ();
    }

    private static String relative(BlockPos offset) {
        return "~" + offset.getX() + " ~" + offset.getY() + " ~" + offset.getZ();
    }

    // --- the dock -----------------------------------------------------------------------------------------------

    /**
     * Builds the first arm and what the arm before it would have placed: its bus and Step-Down, three cells of
     * each line and its barrel. The power line ends one block behind the
     * arm ({@link #powerLineEnd()}) and the bus connects behind it, for the power source and the terminal.
     */
    public void buildDock(ServerLevel level) {
        BlockState data = ModBlocks.DATA_CABLE.get().defaultBlockState();
        BlockState power = ModBlocks.CABLE.get().defaultBlockState();
        for (int i = -1; i <= 1; i++) {
            set(level, frame.at(i, 0, -1), data);
            set(level, frame.at(i, 0, 1), power);
        }
        set(level, frame.at(-1, 0, 0), ModBlocks.SERIAL_BUS.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, frame.forward()));
        set(level, frame.at(1, 0, 0), ModBlocks.STEPDOWN_TRANSFORMER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, frame.back()));
        set(level, frame.at(0, 0, 0), ModBlocks.ROBOTIC_ARM.get().defaultBlockState());
        set(level, firstBarrel(), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, frame.back()));
    }

    /** The power cable at the back of the dock, for the power source to connect to. */
    public BlockPos powerLineEnd() {
        return frame.at(-1, 0, 1);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }
}
