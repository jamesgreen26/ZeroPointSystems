package g_mungus.zps.commands.debug.armchain;

import g_mungus.zps.ZPSMod;
import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.block.cableNetwork.TransformerBlock;
import g_mungus.zps.block.cableNetwork.light_pipe.ScriptTerminalBlock;
import g_mungus.zps.blockentity.CoalBurnerBlockEntity;
import g_mungus.zps.blockentity.PowerCellBlockEntity;
import g_mungus.zps.blockentity.light_pipe.ScriptTerminalBlockEntity;
import g_mungus.zps.networking.ScriptComputerC2SPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Everything an {@link ArmChain} needs to start, in a 5 x 3 x 6 box that can be placed anywhere (as the
 * {@code zps:arm_chain_kit} template): the first arm and its dock, the chain's Script Terminal, a Power Cell kept
 * topped up by a Coal Burner and fed to the chain through a Step-Up, the loader arm with its own terminal and
 * Step-Down, and a double chest holding the kits for {@link #ARMS} arms in the order the loader takes them. One
 * lever starts both terminals.
 * <p>
 * In the kit's frame (forward the way the chain grows, side toward the loader, up), the floor the first arm stands
 * on is at up 1, with the burner and cell under it at up 0:
 * <pre>
 *   side 0: data line, catwalk                    side 3: loader terminal, data cable
 *   side 1: chain terminal, data cable, bus, arm   side 4: data cable, loader bus, loader, loader Step-Down
 *   side 2: lever block (lever on top), Step-Up    side 5: double chest
 *           over the cell, power line, barrel
 * </pre>
 * The chain leaves the box going forward from 5 and grows as long as the chest lasts.
 */
public final class ArmChainKit {
    public static final ResourceLocation TEMPLATE = ZPSMod.resource("arm_chain_kit");
    public static final Vec3i SIZE = new Vec3i(5, 3, 6);
    /** Arms the chest holds kits for: as many whole pairs as fit in a double chest. */
    public static final int ARMS = 6;

    private static final int CELL_CHARGE = 500_000;

    public static final BlockPos FIRST_ARM = new BlockPos(3, 1, 1);
    public static final BlockPos CHAIN_TERMINAL = new BlockPos(0, 1, 1);
    public static final BlockPos LEVER = new BlockPos(0, 2, 2);
    public static final BlockPos LOADER_TERMINAL = new BlockPos(0, 1, 3);
    public static final BlockPos LOADER = new BlockPos(3, 1, 4);
    public static final BlockPos CHEST = new BlockPos(3, 1, 5);

    private ArmChainKit() {
    }

    /**
     * Builds the kit with its corner at {@code corner}, growing toward {@code forward}, with the loader to the
     * right of it.
     */
    public static void build(ServerLevel level, BlockPos corner, Direction forward) {
        Frame kit = new Frame(corner, forward, forward.getClockWise());
        ArmChain chain = new ArmChain(kit.moved(FIRST_ARM.getX(), FIRST_ARM.getY(), FIRST_ARM.getZ()));

        for (int f = 0; f < SIZE.getX(); f++) {
            for (int u = 0; u < SIZE.getY(); u++) {
                for (int s = 0; s < SIZE.getZ(); s++) {
                    level.setBlock(kit.at(f, u, s), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        chain.buildDock(level);

        // Power: the Coal Burner tops up the cell, the Step-Up above the cell feeds the dock's power line.
        set(level, kit.at(0, 0, 2), ModBlocks.COAL_BURNER.get().defaultBlockState());
        set(level, kit.at(1, 0, 2), ModBlocks.POWER_CELL.get().defaultBlockState());
        set(level, kit.at(1, 1, 2), ModBlocks.STEPUP_TRANSFORMER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, Direction.DOWN));
        if (level.getBlockEntity(kit.at(0, 0, 2)) instanceof CoalBurnerBlockEntity burner) {
            burner.getFuelInventory().insertItem(0, new ItemStack(Items.COAL, 64), false);
        }
        if (level.getBlockEntity(kit.at(1, 0, 2)) instanceof PowerCellBlockEntity cell) {
            IEnergyStorage energy = cell.getEnergyStorage(null);
            int stored = 0;
            while (stored < CELL_CHARGE) {
                int got = energy.receiveEnergy(CELL_CHARGE - stored, false);
                if (got <= 0) {
                    break;
                }
                stored += got;
            }
        }

        // The chain's terminal, behind the first bus.
        set(level, kit.at(1, 1, 1), ModBlocks.DATA_CABLE.get().defaultBlockState());
        terminal(level, kit, CHAIN_TERMINAL, chain.chainScript(0));

        // The loader: its terminal, bus, arm and Step-Down, off the dock's power line.
        set(level, kit.at(1, 1, 3), ModBlocks.DATA_CABLE.get().defaultBlockState());
        set(level, kit.at(1, 1, 4), ModBlocks.DATA_CABLE.get().defaultBlockState());
        set(level, kit.at(2, 1, 4), ModBlocks.SERIAL_BUS.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, kit.forward()));
        set(level, kit.at(LOADER.getX(), LOADER.getY(), LOADER.getZ()), ModBlocks.ROBOTIC_ARM.get().defaultBlockState());
        set(level, kit.at(4, 1, 4), ModBlocks.STEPDOWN_TRANSFORMER.get().defaultBlockState()
                .setValue(TransformerBlock.FACING, kit.back()));
        set(level, kit.at(4, 1, 3), ModBlocks.CABLE.get().defaultBlockState());
        terminal(level, kit, LOADER_TERMINAL, ArmChain.loaderScript(
                kit.offset(CHEST.getX() - LOADER_TERMINAL.getX(), CHEST.getY() - LOADER_TERMINAL.getY(), CHEST.getZ() - LOADER_TERMINAL.getZ()),
                kit.offset(3 - LOADER_TERMINAL.getX(), 2 - LOADER_TERMINAL.getY(), 2 - LOADER_TERMINAL.getZ())));

        // The supply: a double chest facing the loader, filled slot by slot in loader order.
        Direction chestFacing = kit.otherSide();
        set(level, kit.at(CHEST.getX(), CHEST.getY(), CHEST.getZ()), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, chestFacing).setValue(ChestBlock.TYPE, ChestType.LEFT));
        set(level, kit.at(CHEST.getX() + 1, CHEST.getY(), CHEST.getZ()), Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, chestFacing).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        fill(supply(level, kit.at(CHEST.getX(), CHEST.getY(), CHEST.getZ())), ArmChain.kits(ARMS));

        // One lever on a block between the two terminals starts both.
        set(level, kit.at(0, 1, 2), ModBlocks.CAUTION_BLOCK.get().defaultBlockState());
        set(level, kit.at(LEVER.getX(), LEVER.getY(), LEVER.getZ()), Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, kit.back()));
    }

    /**
     * Builds the kit facing east at {@code corner} and returns it as template NBT. The arms' hand positions are
     * left out: they are absolute, and would leave a placed kit reaching back to wherever this one was built.
     */
    public static CompoundTag capture(ServerLevel level, BlockPos corner) {
        build(level, corner, Direction.EAST);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, corner, SIZE, false, Blocks.STRUCTURE_VOID);
        template.setAuthor("Zero Point Systems");
        CompoundTag tag = template.save(new CompoundTag());
        for (Tag block : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag nbt = ((CompoundTag) block).getCompound("nbt");
            for (String key : ABSOLUTE_ARM_KEYS) {
                nbt.remove(key);
            }
        }
        return tag;
    }

    private static final List<String> ABSOLUTE_ARM_KEYS =
            List.of("HandBlockPos", "MoveStartBlockPos", "MoveTargetBlockPos", "PendingTransferTargetPos");

    /** The supply chest's whole inventory, both halves, as the loader sees it. */
    public static Container supply(ServerLevel level, BlockPos chest) {
        BlockState state = level.getBlockState(chest);
        Container container = ChestBlock.getContainer((ChestBlock) state.getBlock(), state, level, chest, true);
        if (container == null) {
            throw new IllegalStateException("No chest at " + chest);
        }
        return container;
    }

    private static void fill(Container container, List<ItemStack> stacks) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, slot < stacks.size() ? stacks.get(slot).copy() : ItemStack.EMPTY);
        }
    }

    private static void terminal(ServerLevel level, Frame kit, BlockPos at, String script) {
        BlockPos pos = kit.at(at.getX(), at.getY(), at.getZ());
        set(level, pos, ModBlocks.SCRIPT_TERMINAL.get().defaultBlockState()
                .setValue(ScriptTerminalBlock.FACING, kit.back()));
        if (level.getBlockEntity(pos) instanceof ScriptTerminalBlockEntity terminal) {
            terminal.acceptUpdatePacket(new ScriptComputerC2SPacket(pos, true, ArmChain.DELAY, script));
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }
}
