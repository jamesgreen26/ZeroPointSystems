package g_mungus.zps.blockentity;

import g_mungus.zps.block.PowerCellBlock;
import g_mungus.zps.menu.PowerCellMenu;
import g_mungus.zps.multiblock.MultiblockBlockEntity;
import g_mungus.zps.multiblock.MultiblockPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * A Power Cell block. Cells placed in a {@code w x w x h} box (up to {@link #MAX_WIDTH} wide and
 * {@link #MAX_HEIGHT} tall) join into one battery: the controller at the structure's minimum corner holds the
 * pooled energy and the single charge slot, and every other part forwards to it. Only capacity scales with the
 * number of cells; transfer and item-charging rates are fixed per structure.
 */
public class PowerCellBlockEntity extends MultiblockBlockEntity implements EnergyStorageBE, ItemChargingPowerCell {
    /** Capacity contributed by each cell in a structure. */
    public static final int MAX_ENERGY = 2_097_152;
    /** Transfer rate in and out; fixed per structure, however many cells it has. */
    public static final int MAX_TRANSFER = 16_384;
    /** Item charging rate; fixed per structure, however many cells it has. */
    public static final int ITEM_CHARGE_TRANSFER = 1024;
    public static final int MAX_WIDTH = 3;
    public static final int MAX_HEIGHT = 32;

    /** The divider ring is 3px tall and travels between the two 2px end plates. */
    private static final float RING_HEIGHT_PX = 3.0f;
    private static final float PLATE_HEIGHT_PX = 2.0f;

    private final SyncedEnergyStorage energyStorage = new SyncedEnergyStorage();
    private final ChargeItemStackHandler chargeInventory = new ChargeItemStackHandler();
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getMenuEnergyStored();
                case 1 -> getMenuMaxEnergyStored();
                case 2 -> getChargeItemEnergyStored();
                case 3 -> getChargeItemMaxEnergyStored();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                energyStorage.setEnergyStoredExact(value);
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    @Nullable
    private int[] lastLayerLevels;
    private int lastSentClientEnergy = Integer.MIN_VALUE;
    private int lastComparatorOutput = -1;
    private float clientSmoothedFill = 0.0f;
    private long lastHudInfoRequestTick = Long.MIN_VALUE;
    private int hudInfo;

    public PowerCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CELL.get(), pos, state);
    }

    private class SyncedEnergyStorage extends EnergyStorage {
        private SyncedEnergyStorage() {
            super(MAX_ENERGY, MAX_TRANSFER, MAX_TRANSFER);
        }

        void setEnergyStoredExact(int energy) {
            this.energy = Mth.clamp(energy, 0, this.capacity);
        }

        /** Resize to hold the contents of {@code blocks} cells, discarding anything that no longer fits. */
        void setBlocks(int blocks) {
            this.capacity = MAX_ENERGY * Math.max(1, blocks);
            this.energy = Math.min(this.energy, this.capacity);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                setChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) {
                setChanged();
            }
            return extracted;
        }
    }

    // --- ticking ----------------------------------------------------------------------------------

    public void serverTick() {
        tickMultiblock();
        if (isRemoved() || !isController()) {
            return;
        }
        chargeItem();
        updateFillLevels();
        updateComparatorOutput();
        syncToClient();
    }

    // --- structure --------------------------------------------------------------------------------

    @Override
    public int getMaxWidth() {
        return MAX_WIDTH;
    }

    @Override
    public int getMaxHeight() {
        return MAX_HEIGHT;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public PowerCellBlockEntity getControllerBE() {
        return super.getControllerBE();
    }

    @Override
    public void setContainerSize(int blocks) {
        energyStorage.setBlocks(blocks);
    }

    @Override
    public void absorbContents(MultiblockPart part) {
        if (!(part instanceof PowerCellBlockEntity cell) || cell == this) {
            return;
        }
        int taken = cell.energyStorage.getEnergyStored();
        energyStorage.setEnergyStoredExact(energyStorage.getEnergyStored() + taken);
        cell.energyStorage.setEnergyStoredExact(0);

        ItemStack stack = cell.chargeInventory.getStackInSlot(0);
        if (!stack.isEmpty()) {
            if (chargeInventory.getStackInSlot(0).isEmpty()) {
                chargeInventory.setStackInSlot(0, stack);
            } else if (level != null) {
                BlockPos at = cell.getBlockPos();
                Containers.dropItemStack(level, at.getX(), at.getY(), at.getZ(), stack);
            }
            cell.chargeInventory.setStackInSlot(0, ItemStack.EMPTY);
        }
        cell.setChanged();
        setChanged();
    }

    @Override
    @Nullable
    public Object takeSplitContents() {
        int total = energyStorage.getEnergyStored();
        int keep = isRemoved() ? 0 : Math.min(MAX_ENERGY, total);
        energyStorage.setBlocks(1);
        energyStorage.setEnergyStoredExact(keep);
        return total - keep;
    }

    @Override
    @Nullable
    public Object receiveSplitContents(@Nullable Object contents) {
        if (!(contents instanceof Integer remaining) || remaining <= 0) {
            return contents;
        }
        int space = energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored();
        int take = Math.min(space, remaining);
        energyStorage.setEnergyStoredExact(energyStorage.getEnergyStored() + take);
        setChanged();
        return remaining - take;
    }

    @Override
    protected void onControllerRemoved(boolean keepContents) {
        energyStorage.setBlocks(1);
        if (!keepContents) {
            energyStorage.setEnergyStoredExact(0);
        }
    }

    @Override
    public void notifyMultiUpdated() {
        if (level == null || level.isClientSide()) {
            return;
        }
        lastLayerLevels = null;
        lastComparatorOutput = -1;

        BlockState state = level.getBlockState(worldPosition);
        if (!PowerCellBlock.isCell(state)) {
            return;
        }
        BlockPos offset = getOffsetInStructure();
        PowerCellBlockEntity controller = getControllerBE();
        int fillLevel = controller == null ? state.getValue(PowerCellBlock.LEVEL)
                : controller.fillLevelForLayer(offset.getY());
        BlockState updated = state
                .setValue(PowerCellBlock.BOTTOM, offset.getY() == 0)
                .setValue(PowerCellBlock.TOP, offset.getY() == height - 1)
                .setValue(PowerCellBlock.NORTH, offset.getZ() == 0)
                .setValue(PowerCellBlock.SOUTH, offset.getZ() == width - 1)
                .setValue(PowerCellBlock.WEST, offset.getX() == 0)
                .setValue(PowerCellBlock.EAST, offset.getX() == width - 1)
                .setValue(PowerCellBlock.LEVEL, fillLevel);
        if (updated != state) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        setChanged();
    }

    // --- energy -----------------------------------------------------------------------------------

    public static boolean isChargeable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy != null && energy.canReceive();
    }

    /** The structure's pooled storage; {@code null} while this part's controller is not available. */
    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        PowerCellBlockEntity controller = getControllerBE();
        return controller == null ? null : controller.energyStorage;
    }

    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        PowerCellBlockEntity controller = getControllerBE();
        return controller == null ? null : controller.chargeInventory;
    }

    @Override
    public IItemHandler getChargeInventory() {
        PowerCellBlockEntity controller = getControllerBE();
        return controller == null ? chargeInventory : controller.chargeInventory;
    }

    /** Energy held by the structure this cell belongs to. */
    public int getEnergyStored() {
        PowerCellBlockEntity controller = getControllerBE();
        return controller == null ? 0 : controller.energyStorage.getEnergyStored();
    }

    /** Capacity of the structure this cell belongs to. */
    public int getMaxEnergyStored() {
        PowerCellBlockEntity controller = getControllerBE();
        return controller == null ? 0 : controller.energyStorage.getMaxEnergyStored();
    }

    @Override
    public int getMenuEnergyStored() {
        return getEnergyStored();
    }

    @Override
    public int getMenuMaxEnergyStored() {
        return getMaxEnergyStored();
    }

    public float getFillFraction() {
        int max = getMaxEnergyStored();
        return max <= 0 ? 0.0f : (float) getEnergyStored() / max;
    }

    public int getComparatorOutputSignal() {
        long energyStored = getEnergyStored();
        long maxEnergy = getMaxEnergyStored();
        if (energyStored <= 0 || maxEnergy <= 0) {
            return 0;
        }
        return (int) Math.min(15, Math.ceil((energyStored * 15.0D) / maxEnergy));
    }

    private void chargeItem() {
        if (energyStorage.getEnergyStored() <= 0) {
            return;
        }

        ItemStack stack = chargeInventory.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }

        IEnergyStorage itemEnergy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (itemEnergy == null || !itemEnergy.canReceive()) {
            return;
        }

        int available = energyStorage.extractEnergy(ITEM_CHARGE_TRANSFER, true);
        int accepted = itemEnergy.receiveEnergy(available, true);
        int transfer = Math.min(available, accepted);
        if (transfer <= 0) {
            return;
        }

        int extracted = energyStorage.extractEnergy(transfer, false);
        int received = itemEnergy.receiveEnergy(extracted, false);
        if (received < extracted) {
            energyStorage.receiveEnergy(extracted - received, false);
        }

        chargeInventory.setStackInSlot(0, stack);
        setChanged();
    }

    @Override
    public int getChargeItemEnergyStored() {
        ItemStack stack = getChargeInventory().getStackInSlot(0);
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy == null ? 0 : energy.getEnergyStored();
    }

    @Override
    public int getChargeItemMaxEnergyStored() {
        ItemStack stack = getChargeInventory().getStackInSlot(0);
        IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        return energy == null ? 0 : energy.getMaxEnergyStored();
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack stack = chargeInventory.getStackInSlot(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            chargeInventory.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    // --- visuals ----------------------------------------------------------------------------------

    /** Height the divider ring's centre sits at, in pixels above the structure's floor, for a given fill. */
    public static float ringCentrePx(float fill, int height) {
        float travel = 16.0f * height - 2 * PLATE_HEIGHT_PX - RING_HEIGHT_PX;
        return PLATE_HEIGHT_PX + RING_HEIGHT_PX / 2 + Mth.clamp(fill, 0.0f, 1.0f) * travel;
    }

    /**
     * Block-state fill level for the layer {@code yOffset} blocks above the controller: 0..9 places the
     * charged/uncharged wall boundary at {@code 16 * level / 9} px within the block, which always lands under
     * the divider ring so the seam stays hidden.
     */
    public int fillLevelForLayer(int yOffset) {
        float localCentre = ringCentrePx(getFillFraction(), height) - 16.0f * yOffset;
        return Mth.clamp(Math.round(9.0f * localCentre / 16.0f), 0, 9);
    }

    private void updateFillLevels() {
        if (level == null) {
            return;
        }
        if (lastLayerLevels == null || lastLayerLevels.length != height) {
            lastLayerLevels = new int[height];
            Arrays.fill(lastLayerLevels, -1);
        }
        for (int yOffset = 0; yOffset < height; yOffset++) {
            int fillLevel = fillLevelForLayer(yOffset);
            if (lastLayerLevels[yOffset] == fillLevel) {
                continue;
            }
            lastLayerLevels[yOffset] = fillLevel;
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = worldPosition.offset(xOffset, yOffset, zOffset);
                    BlockState state = level.getBlockState(pos);
                    if (!PowerCellBlock.isCell(state) || state.getValue(PowerCellBlock.LEVEL) == fillLevel) {
                        continue;
                    }
                    level.setBlock(pos, state.setValue(PowerCellBlock.LEVEL, fillLevel),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private void updateComparatorOutput() {
        if (level == null) {
            return;
        }
        int comparatorOutput = getComparatorOutputSignal();
        if (comparatorOutput == lastComparatorOutput) {
            return;
        }
        lastComparatorOutput = comparatorOutput;
        Block block = level.getBlockState(worldPosition).getBlock();
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    level.updateNeighbourForOutputSignal(worldPosition.offset(xOffset, yOffset, zOffset), block);
                }
            }
        }
    }

    private void syncToClient() {
        int energyStored = energyStorage.getEnergyStored();
        if (energyStored == lastSentClientEnergy) {
            return;
        }
        lastSentClientEnergy = energyStored;
        sendBlockEntityUpdate();
    }

    public float getClientSmoothedFill() {
        return clientSmoothedFill;
    }

    public void setClientSmoothedFill(float clientSmoothedFill) {
        this.clientSmoothedFill = clientSmoothedFill;
    }

    // --- HUD --------------------------------------------------------------------------------------

    @Override
    public void setLastHudRefreshTick(long ticks) {
        lastHudInfoRequestTick = ticks;
    }

    @Override
    public long getLastHudRefreshTick() {
        return lastHudInfoRequestTick;
    }

    @Override
    public void provideInfo(Integer info) {
        hudInfo = info;
    }

    @Override
    public Integer getInfo() {
        if (level != null && !level.isClientSide) {
            return getEnergyStored();
        }
        return hudInfo;
    }

    // --- menu -------------------------------------------------------------------------------------

    public Component getDisplayName() {
        return Component.translatable("block.zps.power_cell");
    }

    /**
     * Menu for this cell's structure. The menu is backed by the controller, but validated against
     * {@code accessPos} (the block the player clicked) so tall structures can be used from any part.
     */
    public MenuProvider createMenuProvider(BlockPos accessPos) {
        PowerCellBlockEntity controller = getControllerBE();
        PowerCellBlockEntity target = controller == null ? this : controller;
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new PowerCellMenu(containerId, inventory, target,
                        target.dataAccess, accessPos),
                getDisplayName());
    }

    // --- persistence ------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.put("ChargeItem", chargeInventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        lastLayerLevels = null;
        energyStorage.setBlocks(isController() ? getStructureSize() : 1);
        if (tag.contains("Energy")) {
            energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        }
        if (tag.contains("ChargeItem")) {
            chargeInventory.deserializeNBT(registries, tag.getCompound("ChargeItem"));
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        return tag;
    }

    private class ChargeItemStackHandler extends ItemStackHandler {
        private ChargeItemStackHandler() {
            super(1);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isChargeable(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }
}
