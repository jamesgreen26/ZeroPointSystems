package g_mungus.zps.blockentity;

import g_mungus.zps.block.CoalBurnerBlock;
import g_mungus.zps.menu.CoalBurnerMenu;
import g_mungus.zps.util.TickAverage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CoalBurnerBlockEntity extends BlockEntity implements EnergyGeneratorBE, MenuProvider {
    private static final int MAX_ENERGY = 8192;
    private static final int MAX_OUTPUT = 256;
    private static final int FE_PER_TICK = 32;

    private final GeneratorEnergyStorage energyStorage = new GeneratorEnergyStorage();
    private final FuelItemStackHandler fuelInventory = new FuelItemStackHandler();
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                case 2 -> burnTime;
                case 3 -> totalBurnTime;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergyStoredExact(value);
                case 2 -> burnTime = value;
                case 3 -> totalBurnTime = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    private int burnTime;
    private int totalBurnTime;
    private int currentProductionRate;
    private final TickAverage productionAverage = new TickAverage(HUD_AVERAGE_WINDOW_TICKS);
    private long lastHudInfoRequestTick = Long.MIN_VALUE;
    private int hudInfo;

    public CoalBurnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COAL_BURNER.get(), pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ItemTags.COALS) || stack.is(Items.COAL_BLOCK))
                && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public IItemHandler getFuelInventory() {
        return fuelInventory;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return fuelInventory;
    }

    public void serverTick() {
        currentProductionRate = 0;
        boolean wasBurning = burnTime > 0;

        if (burnTime <= 0 && energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
            consumeFuel();
        }

        if (burnTime == 0 && totalBurnTime > 0) {
            totalBurnTime = 0;
            setChanged();
        } else if (burnTime > 0) {
            burnTime--;
            energyStorage.generateEnergy(FE_PER_TICK);
            currentProductionRate = FE_PER_TICK;
            setChanged();
        }

        pushEnergyToAdjacentBlocks();
        updateLitState(wasBurning || burnTime > 0);

        if (level != null) {
            productionAverage.set(currentProductionRate, level.getGameTime());
        }
    }

    private void pushEnergyToAdjacentBlocks() {
        if (level == null || level.isClientSide() || energyStorage.getEnergyStored() <= 0) {
            return;
        }

        int remainingOutput = MAX_OUTPUT;
        for (Direction side : Direction.values()) {
            if (remainingOutput <= 0 || energyStorage.getEnergyStored() <= 0) {
                break;
            }

            BlockPos targetPos = worldPosition.relative(side);
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null) {
                continue;
            }

            IEnergyStorage targetEnergy = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    targetPos,
                    target.getBlockState(),
                    target,
                    side.getOpposite());
            if (targetEnergy == null || !targetEnergy.canReceive()) {
                continue;
            }

            int available = energyStorage.extractEnergy(remainingOutput, true);
            int accepted = targetEnergy.receiveEnergy(available, true);
            int transfer = Math.min(available, accepted);
            if (transfer <= 0) {
                continue;
            }

            int extracted = energyStorage.extractEnergy(transfer, false);
            int received = targetEnergy.receiveEnergy(extracted, false);
            if (received < extracted) {
                energyStorage.refundEnergy(extracted - received);
            }
            remainingOutput -= received;
            setChanged();
        }
    }

    private void consumeFuel() {
        ItemStack fuel = fuelInventory.getStackInSlot(0);
        if (!isFuel(fuel)) {
            return;
        }

        totalBurnTime = fuel.getBurnTime(RecipeType.SMELTING);
        burnTime = totalBurnTime;
        fuel.shrink(1);
        fuelInventory.setStackInSlot(0, fuel);
        setChanged();
    }

    private void updateLitState(boolean lit) {
        if (level == null) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(CoalBurnerBlock.LIT)) {
            return;
        }

        if (state.getValue(CoalBurnerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(CoalBurnerBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }

        ItemStack stack = fuelInventory.getStackInSlot(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
    }

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
            return productionAverage.average(level.getGameTime());
        }
        return hudInfo;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("BurnTime", burnTime);
        tag.putInt("TotalBurnTime", totalBurnTime);
        tag.put("Fuel", fuelInventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        burnTime = tag.getInt("BurnTime");
        totalBurnTime = tag.getInt("TotalBurnTime");
        if (tag.contains("Fuel")) {
            fuelInventory.deserializeNBT(registries, tag.getCompound("Fuel"));
        }
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.coal_burner");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory,
                                                     @NotNull Player player) {
        return new CoalBurnerMenu(containerId, inventory, this, dataAccess);
    }

    private class FuelItemStackHandler extends ItemStackHandler {
        private FuelItemStackHandler() {
            super(1);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }

    private static class GeneratorEnergyStorage extends EnergyStorage {
        private GeneratorEnergyStorage() {
            super(MAX_ENERGY, 0, MAX_OUTPUT);
        }

        private int generateEnergy(int amount) {
            int inserted = Math.min(Math.max(0, amount), capacity - energy);
            energy += inserted;
            return inserted;
        }

        private void setEnergyStoredExact(int energy) {
            this.energy = Math.max(0, Math.min(this.capacity, energy));
        }

        private void refundEnergy(int amount) {
            energy += Math.min(Math.max(0, amount), capacity - energy);
        }
    }
}
