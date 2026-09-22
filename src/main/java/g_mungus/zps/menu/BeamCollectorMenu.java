package g_mungus.zps.menu;

import g_mungus.zps.block.ModBlocks;
import g_mungus.zps.blockentity.BeamCollectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * The Beam Collector's pooled inventory: 9, 15 or 21 slots depending on the panel, always in three rows. The 21 run
 * from the left edge, which leaves the two rightmost columns clear for the energy bar; the 9 and the 15 are
 * centred. The slot count is fixed when the menu opens, so the menu closes
 * if the panel changes size underneath it.
 */
public class BeamCollectorMenu extends AbstractContainerMenu {
    /** Where the widest grid, seven by three, starts. It fills the space beside the energy bar. */
    private static final int GRID_LEFT = 8;
    private static final int GRID_TOP = 16;
    private static final int GRID_ROWS = 3;
    /** The screen's width, which the narrower grids are centred in, as a dispenser's is. */
    private static final int SCREEN_WIDTH = 176;
    public static final int PLAYER_INV_TOP = 84;
    public static final int HOTBAR_TOP = 142;

    private final BeamCollectorBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos accessPos;
    private final int beamSlots;

    public BeamCollectorMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), buffer.readVarInt());
    }

    private BeamCollectorMenu(int containerId, Inventory inventory, BlockPos accessPos, int slots) {
        // The client's copy of the panel never resizes its inventory, so the slots get a stand-in of the right size.
        this(containerId, inventory, resolveBlockEntity(inventory.player.level(), accessPos),
                new ItemStackHandler(slots), new SimpleContainerData(BeamCollectorBlockEntity.DATA_COUNT), accessPos);
    }

    public BeamCollectorMenu(int containerId, Inventory inventory, BeamCollectorBlockEntity blockEntity,
                           IItemHandler beamInventory, ContainerData data, BlockPos accessPos) {
        super(ModMenus.BEAM_COLLECTOR.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.accessPos = accessPos;
        this.beamSlots = beamInventory.getSlots();
        Level level = blockEntity.getLevel();
        this.access = level == null ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(level, accessPos);

        int columns = columnsFor(beamSlots);
        for (int slot = 0; slot < beamSlots; slot++) {
            addSlot(new SlotItemHandler(beamInventory, slot,
                    gridLeft(beamSlots) + (slot % columns) * 18, gridTop(beamSlots) + (slot / columns) * 18));
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_TOP));
        }

        addDataSlots(data);
    }

    /** Always three rows: 9 slots in a 3x3, 15 in a 5x3, 21 in a 7x3. */
    public static int columnsFor(int slots) {
        return Math.max(1, slots / GRID_ROWS);
    }

    /**
     * Where the first slot goes, across. Seven columns start at the left edge and run up to the energy bar; five
     * and three are centred in the screen, the three exactly where a dispenser's are.
     */
    public static int gridLeft(int slots) {
        int columns = columnsFor(slots);
        return columns >= 7 ? GRID_LEFT : (SCREEN_WIDTH - columns * 18) / 2 + 1;
    }

    /** Where the first slot goes, down. */
    public static int gridTop(int slots) {
        return GRID_TOP;
    }

    private static BeamCollectorBlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BeamCollectorBlockEntity beam) {
            return beam;
        }
        throw new IllegalStateException("Missing beam collector block entity at " + pos);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (!stillValid(access, player, ModBlocks.BEAM_COLLECTOR.get())) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        return !blockEntity.isRemoved() && blockEntity.getInventory().getSlots() == beamSlots;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        int playerStart = beamSlots;
        int hotbarStart = playerStart + 27;
        int end = hotbarStart + 9;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < playerStart) {
                if (!moveItemStackTo(stack, playerStart, end, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, playerStart, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    public int getBeamSlots() {
        return beamSlots;
    }

    public BlockPos getAccessPos() {
        return accessPos;
    }

    public int getEnergyStored() {
        return BeamCollectorBlockEntity.joinData(data.get(0), data.get(1));
    }

    public int getMaxEnergyStored() {
        return BeamCollectorBlockEntity.joinData(data.get(2), data.get(3));
    }
}
