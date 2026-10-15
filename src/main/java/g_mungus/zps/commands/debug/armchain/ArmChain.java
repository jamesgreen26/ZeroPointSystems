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
 * A chain of Robotic Arms that builds itself in a straight 3x3 line and lays a track on top as it goes, working as
 * a bucket brigade: every arm takes a stack from the barrel beside it, places it if it belongs to the arm it is
 * building, and passes anything else on to the barrel of the arm after it. One script runs every arm, and each line
 * acts by what the arm holds, so the order the stacks arrive in is all that drives the build.
 * <p>
 * The arms step up and down between the floor and the level above it, two blocks apart, so that each new arm can
 * be given a Serial Bus behind it and a Step-Down Transformer in front, both facing it. In the {@link Frame} of the
 * chain, forward is the way it grows, side +1 is the power lane (power cables and barrels) and side -1 the data lane
 * (data cables under catwalk, rails on top). With {@code s} = +1 for an arm on the floor and -1 for one on the level
 * above, an arm places, for the next one:
 * <ul>
 *     <li>the arm at (2, s, 0), a Serial Bus at (1, s, 0) with shift-use so it faces forward onto it, and a
 *     Step-Down at (3, s, 0), which faces back onto it;</li>
 *     <li>data cables at (1..3, s, -1) and power cables at (1..3, s, +1);</li>
 *     <li>catwalk over the data cables on the upper level, or a catwalk block where the data line is on the floor;</li>
 *     <li>two rails on the catwalk, and the next arm's barrel at (2, s + 1, +1).</li>
 * </ul>
 * The first arm and the blocks the arm before it would have placed (the dock) are built by hand.
 */
public final class ArmChain {
    /** Ticks between script lines: an arm accepts a new move every 16 ticks. */
    public static final int DELAY = 16;
    /** Script lines in one pass of the chain script, used to keep the loader slower than the chain. */
    public static final int CHAIN_LINES = 11;

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

    /** The stacks arm {@code placer} needs to build the arm after it, in the order it must get them. */
    public static List<ItemStack> kit(int placer) {
        boolean floor = placer % 2 == 0;
        return List.of(
                new ItemStack(ModItems.DATA_CABLE.get(), 3),
                new ItemStack(ModItems.CABLE.get(), 3),
                // Catwalk goes over data cable already laid, so it comes after it; rails go on the catwalk.
                new ItemStack(ModItems.CATWALK.get(), floor ? 3 : 1),
                new ItemStack(Items.RAIL, 2),
                new ItemStack(ModItems.SERIAL_BUS.get()),
                new ItemStack(ModItems.STEPDOWN_TRANSFORMER.get()),
                new ItemStack(ModItems.ROBOTIC_ARM.get()),
                // Last: once the next barrel is down, everything after it is passed on.
                new ItemStack(Items.BARREL));
    }

    /** Kits for {@code arms} arms after the first, in the order the loader feeds them. */
    public static List<ItemStack> kits(int arms) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int placer = 0; placer < arms; placer++) {
            stacks.addAll(kit(placer));
        }
        return stacks;
    }

    // --- scripts ------------------------------------------------------------------------------------------------

    /**
     * The script every arm of the chain runs, on {@link #DELAY} ticks a line, looping, from a terminal
     * {@code floorAboveTerminal} blocks below the level the first arm stands on (negative if the terminal is
     * higher). Every position is relative to the arm running the line, and whether that arm is on the floor or the
     * level above is told from the parity of its height plus the terminal's ({@code pos offset ~ ~ ~ y}), the same
     * as the parity of the difference, so the script works wherever the chain is built.
     */
    public String chainScript(int floorAboveTerminal) {
        String onFloor = "pos offset ~ ~ ~ y & 1 == " + (floorAboveTerminal & 1);
        String floor = "value_of(" + onFloor + ")";
        List<String> lines = new ArrayList<>();
        lines.add("set_transfer_count 64");
        lines.add("take_items value_of(" + at(0, 1, 1) + ")");
        for (int i = 1; i <= 3; i++) {
            lines.add(byLevel(floor,
                    branch(ModItems.DATA_CABLE.get(), "use", at(i, 1, -1), at(i, -1, -1)),
                    branch(ModItems.CABLE.get(), "use", at(i, 1, 1), at(i, -1, 1))));
        }
        lines.add(byLevel(floor,
                branch(ModItems.SERIAL_BUS.get(), "shift_use", at(1, 1, 0), at(1, -1, 0)),
                branch(ModItems.STEPDOWN_TRANSFORMER.get(), "use", at(3, 1, 0), at(3, -1, 0))));
        lines.add(byLevel(floor,
                branch(ModItems.ROBOTIC_ARM.get(), "use", at(2, 1, 0), at(2, -1, 0)),
                branch(Items.BARREL, "use", at(2, 2, 1), at(2, 0, 1))));
        lines.add(byLevel(floor,
                branch(ModItems.CATWALK.get(), "use", at(1, 1, -1), at(2, 0, -1)),
                branch(Items.RAIL, "use", at(1, 2, -1), at(1, 1, -1))));
        lines.add(byLevel(floor,
                branch(ModItems.CATWALK.get(), "use", at(2, 1, -1), null),
                branch(Items.RAIL, "use", at(2, 2, -1), at(2, 1, -1))));
        lines.add(byLevel(floor,
                branch(ModItems.CATWALK.get(), "use", at(3, 1, -1), null)));
        lines.add("if " + onFloor + " put_items value_of(" + at(2, 2, 1) + ") else put_items value_of("
                + at(2, 0, 1) + ")");
        if (lines.size() != CHAIN_LINES) {
            throw new IllegalStateException("Chain script has " + lines.size() + " lines, expected " + CHAIN_LINES);
        }
        return String.join("\n", lines);
    }

    /**
     * The loader's script, looping: two kits a pass (one placed from the floor, one from the level above) from the
     * supply chest into the first arm's barrel, a stack at a time. A take starts at the chest's first full slot, so
     * the chest holds the kits slot by slot in this order; the transfer count is set to each stack's size first,
     * because a take also gathers matching items from later slots. Each stack takes a little longer than a pass of
     * the chain, so the barrel never holds two stacks at once. Once the chest is empty the takes come up empty.
     *
     * @param chest       the supply chest, relative to the loader's terminal
     * @param firstBarrel the first arm's barrel, relative to the loader's terminal
     */
    public static String loaderScript(BlockPos chest, BlockPos firstBarrel) {
        List<String> lines = new ArrayList<>();
        for (ItemStack stack : kits(2)) {
            lines.add("set_transfer_count " + stack.getCount());
            lines.add("take_items " + relative(chest));
            lines.add("put_items " + relative(firstBarrel));
            lines.add("wait " + (CHAIN_LINES + 2 - 3));
        }
        return String.join("\n", lines);
    }

    private record Branch(Item item, String action, String floorTarget, String upperTarget) {
    }

    private static Branch branch(Item item, String action, String floorTarget, String upperTarget) {
        return new Branch(item, action, floorTarget, upperTarget);
    }

    /** One line that acts on whichever of the items the arm holds, at its floor or upper-level position. */
    private static String byLevel(String floor, Branch... branches) {
        List<String> parts = new ArrayList<>();
        for (Branch b : branches) {
            String held = "held_item == " + BuiltInRegistries.ITEM.getKey(b.item());
            parts.add(held + " && " + floor + " " + b.action() + " value_of(" + b.floorTarget() + ")");
            if (b.upperTarget() != null) {
                parts.add(held + " " + b.action() + " value_of(" + b.upperTarget() + ")");
            }
        }
        return "if " + String.join(" else if ", parts);
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
     * each line, its barrel and the catwalk block under its first rail. The power line ends one block behind the
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
        set(level, frame.at(0, 1, -1), ModBlocks.CATWALK.get().defaultBlockState());
    }

    /** The power cable at the back of the dock, for the power source to connect to. */
    public BlockPos powerLineEnd() {
        return frame.at(-1, 0, 1);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }
}
