package g_mungus.zps.blockentity;

import g_mungus.zps.block.BeamCollectorBlock;
import g_mungus.zps.block.ZPSBrushableBlock;
import g_mungus.zps.client.tractor.TractorBeamClientHooks;
import g_mungus.zps.compat.Compat;
import g_mungus.zps.compat.GridSpace;
import g_mungus.zps.entity.Siftable;
import g_mungus.zps.menu.BeamCollectorMenu;
import g_mungus.zps.mixin.ServerGamePacketListenerImplAccessor;
import g_mungus.zps.multiblock.MultiblockBlockEntity;
import g_mungus.zps.multiblock.MultiblockPart;
import g_mungus.zps.tractor.BeamForces;
import g_mungus.zps.tractor.BeamGeometry;
import g_mungus.zps.tractor.BeamScan;
import g_mungus.zps.tractor.BlockRipper;
import g_mungus.zps.tractor.PanelFrame;
import g_mungus.zps.tractor.RunningBeams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Beam Collector block, the machine that projects a tractor beam. Blocks sharing a facing join into a square
 * panel up to three wide; the controller at the panel's minimum corner holds the pooled inventory and energy, runs
 * the beam, and every other part forwards to it.
 * <p>
 * The beam runs while any part of the panel has a redstone signal and the buffer can pay for a tick, and the
 * strength of that signal is what sets its range (see {@link #rangeFor}). The tick cost depends only on the
 * panel's size and that range: what the beam happens to be pulling never changes it.
 */
public class BeamCollectorBlockEntity extends MultiblockBlockEntity {
    public static final int MAX_WIDTH = 3;
    /** Ticks between looks down the columns. A pulled block forces a fresh look straight away. */
    private static final int SCAN_INTERVAL = 5;
    /** The strongest redstone signal there is. */
    private static final int FULL_SIGNAL = 15;
    /** Running cost in FE per tick, per column of the beam, per block of range. It is the only cost. */
    public static final int FE_PER_COLUMN_BLOCK_TICK = 8;
    /** Energy buffer each block of a panel contributes, in FE. */
    public static final int BUFFER_FE_PER_PART = 8192;
    /** Ticks a column waits between pulling blocks loose. */
    private static final int RIP_COOLDOWN_TICKS = 10;
    private static final int DATA_BITS = 15;
    private static final int DATA_MASK = (1 << DATA_BITS) - 1;
    public static final int DATA_COUNT = 4;

    private final PooledInventory inventory = new PooledInventory();
    private final IItemHandler automationView = new ExtractOnlyHandler(inventory);
    private final BeamEnergy energy = new BeamEnergy();

    private boolean active;

    /** The strongest redstone signal reaching any block of the panel, 0 to 15. It is the range control. */
    private int signal;
    private boolean redstoneStale = true;
    @Nullable
    private int[] cooldowns;
    @Nullable
    private BeamScan scan;
    private int scanAge;
    /** What the last split could not hand to any remaining part; see {@link #dropSplitLeftovers}. */
    @Nullable
    private SplitPayload lastSplit;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            // Data slots cross the wire as shorts, and a full panel holds more than a short of energy.
            return switch (index) {
                case 0 -> energy.getEnergyStored() & DATA_MASK;
                case 1 -> energy.getEnergyStored() >>> DATA_BITS;
                case 2 -> energy.getMaxEnergyStored() & DATA_MASK;
                case 3 -> energy.getMaxEnergyStored() >>> DATA_BITS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public BeamCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BEAM_COLLECTOR.get(), pos, state);
    }

    /** Joins the two halves of an energy value sent through {@link ContainerData}. */
    public static int joinData(int low, int high) {
        return (high << DATA_BITS) | (low & DATA_MASK);
    }

    // --- structure --------------------------------------------------------------------------------

    /**
     * Slots in the pooled inventory of a panel made of {@code blocks} blocks: 9, 15 or 21. Always three rows, of
     * three, five or seven, which is what the screen lays them out as.
     */
    public static int slotsFor(int blocks) {
        if (blocks >= 9) {
            return 21;
        }
        return blocks >= 4 ? 15 : 9;
    }

    @Override
    public int getMaxWidth() {
        return MAX_WIDTH;
    }

    /** A panel is a single layer. */
    @Override
    public int getMaxHeight() {
        return 1;
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return getFacing().getAxis();
    }

    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BeamCollectorBlock.FACING) ? state.getValue(BeamCollectorBlock.FACING) : Direction.NORTH;
    }

    /** Two panels back to back share an axis but not a facing, and must stay two panels. */
    @Override
    public boolean canMergeWith(MultiblockPart other) {
        return other instanceof BeamCollectorBlockEntity beam && beam.getFacing() == getFacing();
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public BeamCollectorBlockEntity getControllerBE() {
        return super.getControllerBE();
    }

    @Override
    public void setContainerSize(int blocks) {
        energy.setBlocks(blocks);
        spill(inventory.resize(slotsFor(blocks)), worldPosition);
    }

    @Override
    public void absorbContents(MultiblockPart part) {
        if (!(part instanceof BeamCollectorBlockEntity other) || other == this) {
            return;
        }
        energy.setStored(energy.getEnergyStored() + other.energy.getEnergyStored());
        other.energy.setStored(0);

        // Slot counts are not proportional to size (four singles hold 36 stacks, a 2x2 only 15), so a merge can
        // overflow. What does not fit falls out of the block that brought it.
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot = 0; slot < other.inventory.getSlots(); slot++) {
            ItemStack stack = other.inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            other.inventory.setStackInSlot(slot, ItemStack.EMPTY);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, stack, false);
            if (!remainder.isEmpty()) {
                overflow.add(remainder);
            }
        }
        spill(overflow, other.getBlockPos());
        other.setChanged();
        setChanged();
    }

    @Override
    @Nullable
    public Object takeSplitContents() {
        SplitPayload payload = new SplitPayload();
        payload.energy = energy.getEnergyStored();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                payload.stacks.add(stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        energy.setBlocks(1);
        energy.setStored(0);
        inventory.resize(slotsFor(1));
        if (!isRemoved()) {
            receiveSplitContents(payload);
        }
        lastSplit = payload;
        setChanged();
        return payload;
    }

    @Override
    @Nullable
    public Object receiveSplitContents(@Nullable Object contents) {
        if (!(contents instanceof SplitPayload payload)) {
            return contents;
        }
        int space = energy.getMaxEnergyStored() - energy.getEnergyStored();
        int taken = Math.min(space, payload.energy);
        energy.setStored(energy.getEnergyStored() + taken);
        payload.energy -= taken;

        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : payload.stacks) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, stack, false);
            if (!remainder.isEmpty()) {
                left.add(remainder);
            }
        }
        payload.stacks.clear();
        payload.stacks.addAll(left);
        setChanged();
        return payload;
    }

    /**
     * {@link g_mungus.zps.multiblock.ConnectivityHandler} throws away whatever no part took. Called on the old
     * controller once the split is done, so those items land where the panel was broken instead of vanishing.
     */
    public void dropSplitLeftovers(BlockPos at) {
        if (lastSplit != null) {
            spill(lastSplit.stacks, at);
            lastSplit = null;
        }
    }

    @Override
    protected void onControllerRemoved(boolean keepContents) {
        energy.setBlocks(1);
        spill(inventory.resize(slotsFor(1)), worldPosition);
        if (!keepContents) {
            energy.setStored(0);
            inventory.clear();
        }
        scan = null;
        cooldowns = null;
        setActive(false);
    }

    @Override
    public void notifyMultiUpdated() {
        if (level == null || level.isClientSide()) {
            return;
        }
        scan = null;
        cooldowns = null;
        redstoneStale = true;
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null) {
            controllerBE.redstoneStale = true;
        }

        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof BeamCollectorBlock)) {
            return;
        }
        PanelFrame frame = PanelFrame.of(state.getValue(BeamCollectorBlock.FACING));
        BlockPos offset = getOffsetInStructure();
        BlockState updated = state
                .setValue(BeamCollectorBlock.SIZE, width)
                .setValue(BeamCollectorBlock.COLUMN, indexAlong(offset, frame.right()))
                .setValue(BeamCollectorBlock.ROW, indexAlong(offset, frame.down()))
                .setValue(BeamCollectorBlock.POWER, controllerBE != null ? controllerBE.signal : 0);
        if (updated != state) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        setChanged();
    }

    /**
     * How many blocks the part {@code offset} blocks from the minimum corner is from the panel's rim opposite
     * {@code direction}: 0 on that rim, {@code width - 1} on the rim in {@code direction}.
     */
    private int indexAlong(BlockPos offset, Direction direction) {
        int along = offset.get(direction.getAxis());
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : width - 1 - along;
    }

    /**
     * Writes the redstone signal into every block of the panel, which is what colours its lamps. They follow the
     * signal alone, whether or not the beam can afford to run. Only meaningful on the controller.
     */
    private void setPower(int power) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BeamGeometry beam = geometry();
        for (int column = 0; column < beam.columns(); column++) {
            BlockPos pos = beam.columnBase(column);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BeamCollectorBlock && state.getValue(BeamCollectorBlock.POWER) != power) {
                level.setBlock(pos, state.setValue(BeamCollectorBlock.POWER, power),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private void spill(List<ItemStack> stacks, BlockPos at) {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (ItemStack stack : stacks) {
            Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack);
        }
        stacks.clear();
    }

    public void dropContentsIfStandalone() {
        if (width > 1 || !isController()) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            stacks.add(inventory.getStackInSlot(slot));
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        spill(stacks, worldPosition);
    }

    // --- settings ---------------------------------------------------------------------------------

    /**
     * The range a redstone signal of {@code signal} sets on a panel {@code width} blocks wide.
     * <p>
     * All three sizes use the whole of the signal's fifteen steps, each over its own reach. A single block goes up
     * a block of range on every odd strength, 1 at strength 1 to 8 at 15. A 2x2 has a block of range for each
     * step. A 3x3 has two. With no signal there is no beam.
     */
    public static int rangeFor(int width, int signal) {
        return switch (width) {
            case 1 -> (signal + 1) / 2;
            case 2 -> signal;
            default -> signal * 2;
        };
    }

    /** The beam's length at full signal: 8, 15 or 30. */
    public int getMaxRange() {
        return rangeFor(width, FULL_SIGNAL);
    }

    /** Beam length in use, which is whatever the redstone signal asks for. */
    public int getRange() {
        return rangeFor(width, signal);
    }

    /** The strongest redstone signal reaching the panel, as of the last time it was looked at. */
    public int getSignal() {
        return signal;
    }

    public boolean isActive() {
        return active;
    }

    private void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (!active) {
            scan = null;
        }
        sendBlockEntityUpdate();
    }

    /** FE per tick while running: every column pays for every block of range. Nothing else costs energy. */
    public int getTickCost() {
        return width * width * getRange() * FE_PER_COLUMN_BLOCK_TICK;
    }

    public void markRedstoneStale() {
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        if (controllerBE != null) {
            controllerBE.redstoneStale = true;
        }
    }

    private void refreshRedstone() {
        redstoneStale = false;
        if (level == null) {
            return;
        }
        int strongest = 0;
        BeamGeometry beam = geometry();
        for (int column = 0; column < beam.columns(); column++) {
            strongest = Math.max(strongest, level.getBestNeighborSignal(beam.columnBase(column)));
        }
        if (strongest != signal) {
            signal = strongest;
            // The beam is a different length now: look down it afresh, and tell the client, which draws it.
            scan = null;
            setChanged();
            sendBlockEntityUpdate();
        }
        // Every time, not only on a change: a part just cut loose from a panel still wears that panel's state.
        setPower(signal);
    }

    public BeamGeometry geometry() {
        return new BeamGeometry(worldPosition, getFacing(), width, getRange());
    }

    // --- ticking ----------------------------------------------------------------------------------

    public void serverTick() {
        tickMultiblock();
        if (isRemoved() || !isController() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (redstoneStale) {
            refreshRedstone();
        }

        int cost = getTickCost();
        boolean running = getRange() > 0 && energy.getEnergyStored() >= cost;
        setActive(running);
        if (!running) {
            return;
        }
        energy.setStored(energy.getEnergyStored() - cost);
        setChanged();

        BeamGeometry beam = geometry();
        if (scan == null || scan.columns() != beam.columns() || ++scanAge >= SCAN_INTERVAL) {
            rescan(beam);
        }
        // So a player can find out for itself that it is in a beam; see PlayerWeightlessness.
        RunningBeams.signIn(serverLevel, beam, scan);

        // The ship or sublevel the panel rides, if any: what it carries has to keep pace with it.
        GridSpace carrier = Compat.gridOf(serverLevel, worldPosition);

        List<FallingBlockEntity> inFlight = new ArrayList<>();
        // Wider than the beam, for the falling blocks that start out beside it; grip() sorts out the rest.
        AABB search = beam.worldBounds(serverLevel).inflate(BeamForces.LOOSE_BLOCK_SLACK);
        for (Entity entity : serverLevel.getEntities((Entity) null, search, BeamForces::affects)) {
            BeamForces.Grip grip = BeamForces.grip(serverLevel, beam, scan, entity);
            if (grip == null) {
                continue;
            }
            if (entity instanceof ServerPlayer player) {
                // The player's own client moves them.
                excuseHovering(player);
                continue;
            }
            if (entity.getControllingPassenger() instanceof ServerPlayer rider) {
                // Likewise what a player is steering; the server's push below is simply overruled.
                excuseHovering(rider);
            }
            if (BeamForces.isCargo(entity) && grip.atMouth() && collect(entity)) {
                continue;
            }
            BeamForces.apply(serverLevel, beam, entity, grip, carrier);
            if (entity instanceof FallingBlockEntity falling && falling.isAlive()) {
                inFlight.add(falling);
            }
        }

        pullBlocks(serverLevel, beam, carrier, inFlight);
    }

    /** Hovering in a beam would otherwise count toward the server's kick for flying. */
    private static void excuseHovering(ServerPlayer player) {
        ServerGamePacketListenerImplAccessor connection = (ServerGamePacketListenerImplAccessor) player.connection;
        connection.zps$setAboveGroundTickCount(0);
        connection.zps$setAboveGroundVehicleTickCount(0);
        player.resetFallDistance();
    }

    public void clientTick() {
        if (isController() && active && level != null) {
            TractorBeamClientHooks.tick(this);
        }
    }

    private void rescan(BeamGeometry beam) {
        scan = BeamScan.scan(level, beam);
        scanAge = 0;
    }

    /** Swallows an item or falling block at the mouth. False when it did not all fit and is still out there. */
    private boolean collect(Entity entity) {
        if (entity instanceof ItemEntity item) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, item.getItem().copy(), false);
            if (remainder.isEmpty()) {
                item.discard();
                return true;
            }
            item.setItem(remainder);
            return false;
        }
        if (entity instanceof FallingBlockEntity falling) {
            Block block = falling.getBlockState().getBlock();
            if (block instanceof ZPSBrushableBlock && falling instanceof Siftable siftable) {
                // Banks the buried loot and swaps in plain sand or gravel, which arrives here next tick.
                siftable.sift(inventory);
                return true;
            }
            Item item = block.asItem();
            if (item == Items.AIR) {
                falling.discard();
                return true;
            }
            ItemStack stack = new ItemStack(item);
            if (!ItemHandlerHelper.insertItemStacked(inventory, stack, true).isEmpty()) {
                return false;
            }
            ItemHandlerHelper.insertItemStacked(inventory, stack, false);
            falling.discard();
            return true;
        }
        return false;
    }

    private void pullBlocks(ServerLevel serverLevel, BeamGeometry beam, @Nullable GridSpace carrier,
                            List<FallingBlockEntity> inFlight) {
        if (cooldowns == null || cooldowns.length != beam.columns()) {
            cooldowns = new int[beam.columns()];
        }
        boolean looked = scanAge == 0;
        @Nullable ItemStackHandler reserved = null;

        for (int column = 0; column < beam.columns(); column++) {
            if (cooldowns[column] > 0) {
                cooldowns[column]--;
                continue;
            }
            if (!looked) {
                // About to act on what the column holds, so act on what it holds now.
                rescan(beam);
                looked = true;
            }
            BlockPos target = scan.target(column);
            BeamScan.ColumnHit hit = scan.hit(column);
            if (target == null || (hit != BeamScan.ColumnHit.PULLABLE && hit != BeamScan.ColumnHit.DESTROYABLE)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(target);

            if (hit == BeamScan.ColumnHit.PULLABLE) {
                // Only pull loose what there will be room for, counting the blocks already on their way.
                if (reserved == null) {
                    reserved = reservedInventory(inFlight);
                }
                ItemStack arriving = new ItemStack(state.getBlock().asItem());
                if (!ItemHandlerHelper.insertItemStacked(reserved, arriving, true).isEmpty()) {
                    continue;
                }
                if (!BlockRipper.mayTake(serverLevel, target, state)) {
                    cooldowns[column] = RIP_COOLDOWN_TICKS;
                    continue;
                }
                ItemHandlerHelper.insertItemStacked(reserved, arriving, false);
                BlockRipper.pullLoose(serverLevel, beam, carrier, target, state);
            } else {
                if (!BlockRipper.mayTake(serverLevel, target, state)) {
                    cooldowns[column] = RIP_COOLDOWN_TICKS;
                    continue;
                }
                BlockRipper.destroy(serverLevel, target);
            }
            cooldowns[column] = RIP_COOLDOWN_TICKS;
            // The column is a block longer now; look again before the next tick's entities are gripped.
            scan = null;
            rescan(beam);
        }
    }

    /** A scratch copy of the inventory with every block already in flight put away in it. */
    private ItemStackHandler reservedInventory(List<FallingBlockEntity> inFlight) {
        ItemStackHandler copy = new ItemStackHandler(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            copy.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        for (FallingBlockEntity falling : inFlight) {
            Item item = falling.getBlockState().getBlock().asItem();
            if (item != Items.AIR) {
                ItemHandlerHelper.insertItemStacked(copy, new ItemStack(item), false);
            }
        }
        return copy;
    }

    // --- capabilities -----------------------------------------------------------------------------

    /** Automation may only take from the panel: what goes in is what the beam brings. */
    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        return controllerBE == null ? null : controllerBE.automationView;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        return controllerBE == null ? null : controllerBE.energy;
    }

    /** The pooled inventory itself, for the menu and for tests. */
    public ItemStackHandler getInventory() {
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        return controllerBE == null ? inventory : controllerBE.inventory;
    }

    public int getEnergyStored() {
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        return controllerBE == null ? 0 : controllerBE.energy.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        return controllerBE == null ? 0 : controllerBE.energy.getMaxEnergyStored();
    }

    // --- menu -------------------------------------------------------------------------------------

    /**
     * Opens the panel's menu. It is backed by the controller but validated against {@code accessPos}, the block
     * the player clicked, and it is tied to the panel's current size: the slot count is part of the menu.
     */
    public void openMenu(ServerPlayer player, BlockPos accessPos) {
        BeamCollectorBlockEntity controllerBE = getControllerBE();
        BeamCollectorBlockEntity target = controllerBE == null ? this : controllerBE;
        int slots = target.inventory.getSlots();
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, playerInventory, opener) -> new BeamCollectorMenu(containerId, playerInventory,
                                target, target.inventory, target.dataAccess, accessPos),
                        Component.translatable("block.zps.beam_collector")),
                buffer -> {
                    buffer.writeBlockPos(accessPos);
                    buffer.writeVarInt(slots);
                });
    }

    // --- persistence ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        int blocks = isController() ? getStructureSize() : 1;
        energy.setBlocks(blocks);
        if (tag.contains("Energy")) {
            energy.setStored(tag.getInt("Energy"));
        }
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
            // The saved slot count wins on load; bring it back in line with the structure.
            inventory.resize(slotsFor(blocks));
        }
        if (tag.contains("Signal")) {
            signal = tag.getInt("Signal");
        }
        if (tag.contains("Active")) {
            active = tag.getBoolean("Active");
        }
        redstoneStale = true;
        scan = null;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // Neither is saved: both are worked out again on the first tick. They are here for the client, which
        // needs the signal to know how long a beam to draw and predict.
        tag.putInt("Signal", signal);
        tag.putBoolean("Active", active);
        return tag;
    }

    // --- storage ----------------------------------------------------------------------------------

    /** What a dissolving panel hands round its parts. Mutable: each part takes its share out of it. */
    private static final class SplitPayload {
        private int energy;
        private final List<ItemStack> stacks = new ArrayList<>();
    }

    private final class PooledInventory extends ItemStackHandler {
        private PooledInventory() {
            super(slotsFor(1));
        }

        /** Changes the slot count, keeping what fits. Returns the stacks that no longer do. */
        List<ItemStack> resize(int slots) {
            List<ItemStack> overflow = new ArrayList<>();
            if (slots == stacks.size()) {
                return overflow;
            }
            NonNullList<ItemStack> old = stacks;
            stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
            for (ItemStack stack : old) {
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(this, stack, false);
                if (!remainder.isEmpty()) {
                    overflow.add(remainder);
                }
            }
            setChanged();
            return overflow;
        }

        void clear() {
            for (int slot = 0; slot < stacks.size(); slot++) {
                stacks.set(slot, ItemStack.EMPTY);
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }

    /** The panel only ever receives energy; its capacity follows the panel's size. */
    private final class BeamEnergy implements IEnergyStorage {
        private int stored;
        private int blocks = 1;

        void setBlocks(int blocks) {
            this.blocks = Math.max(1, blocks);
            stored = Math.min(stored, getMaxEnergyStored());
        }

        void setStored(int stored) {
            this.stored = Mth.clamp(stored, 0, getMaxEnergyStored());
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int received = Mth.clamp(getMaxEnergyStored() - stored, 0, Math.max(0, toReceive));
            if (received > 0 && !simulate) {
                stored += received;
                setChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER_FE_PER_PART * blocks;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private record ExtractOnlyHandler(IItemHandler delegate) implements IItemHandler {
        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    }
}
