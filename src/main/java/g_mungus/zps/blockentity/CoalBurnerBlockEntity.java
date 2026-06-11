package g_mungus.zps.blockentity;

import g_mungus.zps.block.CoalBurnerBlock;
import g_mungus.zps.menu.CoalBurnerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CoalBurnerBlockEntity extends BlockEntity implements EnergyStorageBE, MenuProvider {
    private static final int MAX_ENERGY = 64_000;
    private static final int MAX_OUTPUT = 256;
    private static final int FE_PER_TICK = 40;

    private final GeneratorEnergyStorage energyStorage = new GeneratorEnergyStorage();
    private final FuelItemStackHandler fuelInventory = new FuelItemStackHandler();
    private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> energyStorage);
    private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> fuelInventory);
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
    private long lastHudInfoRequestTick = Long.MIN_VALUE;
    private int hudInfo;

    public CoalBurnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COAL_BURNER.get(), pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ItemTags.COALS) || stack.is(Items.COAL_BLOCK))
                && ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
    }

    public IItemHandler getFuelInventory() {
        return fuelInventory;
    }

    public void serverTick() {
        pushEnergy();

        if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
            if (burnTime <= 0) {
                consumeFuel();
            }

            if (burnTime > 0) {
                burnTime--;
                energyStorage.generateEnergy(FE_PER_TICK);
                setChanged();
            }
        }

        updateLitState();
    }

    private void pushEnergy() {
        if (level == null || energyStorage.getEnergyStored() <= 0) {
            return;
        }

        for (Direction side : Direction.values()) {
            if (energyStorage.getEnergyStored() <= 0) {
                return;
            }

            BlockEntity target = level.getBlockEntity(worldPosition.relative(side));
            if (target == null) {
                continue;
            }

            target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(storage -> {
                if (!storage.canReceive()) {
                    return;
                }

                int available = energyStorage.extractEnergy(MAX_OUTPUT, true);
                int accepted = storage.receiveEnergy(available, true);
                int toSend = Math.min(available, accepted);
                if (toSend > 0) {
                    int extracted = energyStorage.extractEnergy(toSend, false);
                    storage.receiveEnergy(extracted, false);
                    setChanged();
                }
            });
        }
    }

    private void consumeFuel() {
        ItemStack fuel = fuelInventory.getStackInSlot(0);
        if (!isFuel(fuel)) {
            return;
        }

        totalBurnTime = ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
        burnTime = totalBurnTime;
        fuel.shrink(1);
        fuelInventory.setStackInSlot(0, fuel);
        setChanged();
    }

    private void updateLitState() {
        if (level == null) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(CoalBurnerBlock.LIT)) {
            return;
        }

        boolean lit = burnTime > 0;
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
            return energyStorage.getEnergyStored();
        }
        return hudInfo;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("BurnTime", burnTime);
        tag.putInt("TotalBurnTime", totalBurnTime);
        tag.put("Fuel", fuelInventory.serializeNBT());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergyStoredExact(tag.getInt("Energy"));
        burnTime = tag.getInt("BurnTime");
        totalBurnTime = tag.getInt("TotalBurnTime");
        if (tag.contains("Fuel")) {
            fuelInventory.deserializeNBT(tag.getCompound("Fuel"));
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energy.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return items.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energy.invalidate();
        items.invalidate();
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

        private void generateEnergy(int amount) {
            energy = Math.min(capacity, energy + amount);
        }

        private void setEnergyStoredExact(int energy) {
            this.energy = Math.max(0, Math.min(this.capacity, energy));
        }
    }
}
