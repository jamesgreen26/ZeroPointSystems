package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.VaporizerBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.menu.VaporizerMenu;
import g_mungus.zps.recipe.GasOutput;
import g_mungus.zps.recipe.ModRecipes;
import g_mungus.zps.recipe.VaporizingInput;
import g_mungus.zps.recipe.VaporizingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.util.GasPhysics;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns items into gas according to {@link VaporizingRecipe}s, paying for each craft in heat.
 *
 * <p>The machine carries a temperature of its own, separate from the gas at its node. A recipe runs
 * only once the machine is at least as hot as the recipe asks; while a matching set of items is
 * waiting below that, the heater burns FE from the internal buffer to close the gap. Each craft
 * emits its gas at the machine's current temperature and then drops the machine by the recipe's
 * cost, so a run of crafts alternates heating and vaporizing.
 *
 * <p>The gas lands in this block's own Kelvin node — a tank — and leaves through whatever ducts are
 * hung off it. Vaporizing pauses when the tank is close to its pressure ceiling rather than letting
 * the machine burst itself.
 */
public class VaporizerBlockEntity extends GasNodeBlockEntity implements MenuProvider {
    public static final int SLOT_COUNT = 3;

    private static final int MAX_ENERGY = 8192;
    private static final int MAX_RECEIVE = 512;

    /** Where a freshly placed machine starts, and Kelvin's own ambient. */
    public static final double AMBIENT_TEMPERATURE_K = 273.15;
    /** Absolute zero would divide by nothing downstream, so the machine never quite gets there. */
    public static final double MIN_TEMPERATURE_K = 1.0;
    private static final double MAX_STORED_TEMPERATURE_K = 1.0e6;

    /** FE to raise the machine by one Kelvin. */
    public static final int FE_PER_KELVIN = 40;
    /** The most FE the heater draws in a tick — two Kelvin per tick at full power. */
    public static final int MAX_HEAT_FE_PER_TICK = 80;

    /** How full, against the node's pressure ceiling, the tank may get before vaporizing pauses. */
    public static final double FULL_FRACTION = 0.9;

    /** What the machine is doing this tick, for the screen. */
    public enum Status {
        IDLE,
        HEATING,
        NO_POWER,
        VAPORIZING,
        OUTPUT_FULL;

        private static final Status[] VALUES = values();

        public static Status byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
        }
    }

    private static final String ENERGY_KEY = "Energy";
    private static final String TEMPERATURE_KEY = "Temperature";
    private static final String INVENTORY_KEY = "Inventory";
    private static final String GASES_KEY = "Gases";
    private static final String GAS_KEY = "Gas";
    private static final String MASS_KEY = "Mass";

    private final VaporizerEnergyStorage energyStorage = new VaporizerEnergyStorage();
    private final VaporizerInventory inventory = new VaporizerInventory();
    /** Capability view: automation feeds the slots but never takes ingredients back out. */
    private final IItemHandler automationHandler = new InsertOnlyHandler(inventory);

    private double temperatureK = AMBIENT_TEMPERATURE_K;
    private Status status = Status.IDLE;
    /** The temperature the heater is working toward, or zero when it is not heating. */
    private double targetTemperatureK;
    @Nullable
    private VaporizingRecipe cachedRecipe;

    /** Client-side mirror of what is in the tank, by gas id, for the screen's buffer tooltip. */
    private Map<ResourceLocation, Double> clientGasMasses = new LinkedHashMap<>();
    /** What was last pushed to clients, so a block update only goes out when the contents move. */
    private Map<ResourceLocation, Double> lastSentGasMasses = new HashMap<>();

    /** Synced to the menu: energy, capacity, temperature and target in tenths of a Kelvin, status. */
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                case 2 -> (int) Math.round(temperatureK * 10);
                case 3 -> (int) Math.round(targetTemperatureK * 10);
                case 4 -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergyStoredExact(value);
                case 2 -> temperatureK = value / 10.0;
                case 3 -> targetTemperatureK = value / 10.0;
                case 4 -> status = Status.byOrdinal(value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public VaporizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VAPORIZER.get(), pos, state);
    }

    // --- capabilities -----------------------------------------------------------------------

    /** Capability handler for hoppers and pipes: insert only. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return automationHandler;
    }

    /** Unrestricted handler for the GUI, so players can take their ingredients back. */
    public IItemHandler getMenuInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    // --- state ------------------------------------------------------------------------------

    /** The machine's own temperature, in Kelvin. Not the gas temperature at its node. */
    public double getMachineTemperature() {
        return temperatureK;
    }

    public Status getStatus() {
        return status;
    }

    /** Set the machine temperature directly, clamped to what it can hold. For tests and commands. */
    public void setMachineTemperature(double kelvin) {
        temperatureK = clampTemperature(kelvin);
        setChanged();
    }

    /** The temperature the heater is aiming for, or zero when idle. */
    public double getTargetTemperature() {
        return targetTemperatureK;
    }

    /** Client-side: what the tank holds, by gas id, in kilograms. */
    public Map<ResourceLocation, Double> getClientGasMasses() {
        return clientGasMasses;
    }

    /** The node's pressure ceiling, so the screen can draw the buffer's fill level. */
    public double getMaxPressure() {
        return VaporizerBlock.MAX_PRESSURE;
    }

    // --- work -------------------------------------------------------------------------------

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        restoreNodeIfMissing();

        VaporizingRecipe recipe = getActiveRecipe();
        if (recipe == null) {
            setStatus(Status.IDLE, 0);
        } else if (temperatureK < recipe.minTemperature()) {
            heatToward(recipe.minTemperature());
        } else if (!hasRoomFor(recipe)) {
            setStatus(Status.OUTPUT_FULL, 0);
        } else {
            vaporize(recipe);
            setStatus(Status.VAPORIZING, 0);
        }

        if (syncNodeState()) {
            syncGasContentsIfChanged();
        }
    }

    /**
     * Burn FE to close the gap to {@code target}, at most {@link #MAX_HEAT_FE_PER_TICK} a tick and
     * never past the target itself.
     */
    private void heatToward(double target) {
        double deficitK = target - temperatureK;
        double maxStepK = (double) MAX_HEAT_FE_PER_TICK / FE_PER_KELVIN;
        int feWanted = (int) Math.ceil(Math.min(deficitK, maxStepK) * FE_PER_KELVIN);
        int fe = Math.min(feWanted, energyStorage.getEnergyStored());
        if (fe <= 0) {
            setStatus(Status.NO_POWER, target);
            return;
        }
        energyStorage.consume(fe);
        temperatureK = Math.min(target, temperatureK + (double) fe / FE_PER_KELVIN);
        setStatus(Status.HEATING, target);
        setChanged();
    }

    /** Take one item per ingredient, emit the gas at the current temperature, then pay the cost. */
    private void vaporize(VaporizingRecipe recipe) {
        int[] slots = recipe.findSlotAssignment(VaporizingInput.of(inventory));
        if (slots == null) {
            cachedRecipe = null;
            return;
        }

        // Read before paying: the gas comes out at the temperature the machine reached, not the
        // one it is left at.
        double emitAt = temperatureK;
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos node = getDuctNodePosition();
        for (GasOutput output : recipe.results()) {
            GasType gas = output.resolve();
            if (gas != null) {
                kelvin.addGasAtTemperature(node, gas, output.massKg(), emitAt);
            }
        }

        for (int slot : slots) {
            inventory.getStackInSlot(slot).shrink(1);
        }
        inventory.onContentsChanged(-1);

        temperatureK = Math.max(MIN_TEMPERATURE_K, temperatureK - recipe.temperatureCost());
        cachedRecipe = null;
        setChanged();
    }

    /**
     * Whether the tank can take this recipe's gas without crossing {@link #FULL_FRACTION} of its
     * pressure ceiling. Predicted from the ideal gas law on what would be in the tank afterwards,
     * at the hotter of the tank and the machine — a conservative figure, since the mix would
     * actually settle somewhere between.
     */
    private boolean hasRoomFor(VaporizingRecipe recipe) {
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos nodePos = getDuctNodePosition();
        DuctNode node = kelvin.getNodeAt(nodePos);
        if (node == null) {
            return false;
        }
        Map<GasType, Double> after = new HashMap<>(kelvin.getGasMassAt(nodePos));
        for (GasOutput output : recipe.results()) {
            GasType gas = output.resolve();
            if (gas != null) {
                after.merge(gas, output.massKg(), Double::sum);
            }
        }
        double temperature = Math.max(kelvin.getTemperatureAt(nodePos), temperatureK);
        double predicted = GasPhysics.INSTANCE.calcPressureFromGamma(after, node.getVolume(), temperature);
        return predicted < node.getMaxPressure() * FULL_FRACTION;
    }

    @Nullable
    private VaporizingRecipe getActiveRecipe() {
        VaporizingInput input = VaporizingInput.of(inventory);
        if (input.isEmpty()) {
            cachedRecipe = null;
            return null;
        }
        if (cachedRecipe != null && cachedRecipe.matches(input, level)) {
            return cachedRecipe;
        }
        cachedRecipe = level.getRecipeManager()
                .getRecipeFor(ModRecipes.VAPORIZING_TYPE.get(), input, level)
                .map(RecipeHolder::value)
                .orElse(null);
        return cachedRecipe;
    }

    private void setStatus(Status newStatus, double target) {
        status = newStatus;
        targetTemperatureK = target;
    }

    private boolean hasVaporizingRecipeAccepting(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (RecipeHolder<VaporizingRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.VAPORIZING_TYPE.get())) {
            if (holder.value().accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    // --- client sync ------------------------------------------------------------------------

    /** Push the tank's contents to clients when they have moved since the last push. */
    private void syncGasContentsIfChanged() {
        Map<ResourceLocation, Double> current = new HashMap<>();
        for (Map.Entry<GasType, Double> entry : getGases().entrySet()) {
            if (entry.getValue() > 1e-9) {
                current.put(entry.getKey().getResourceLocation(), entry.getValue());
            }
        }
        if (sameContents(current, lastSentGasMasses)) {
            return;
        }
        lastSentGasMasses = current;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private static boolean sameContents(Map<ResourceLocation, Double> a, Map<ResourceLocation, Double> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<ResourceLocation, Double> entry : a.entrySet()) {
            if (Math.abs(entry.getValue() - b.get(entry.getKey())) > 1e-4) {
                return false;
            }
        }
        return true;
    }

    private void writeClientState(CompoundTag tag) {
        tag.putInt(ENERGY_KEY, energyStorage.getEnergyStored());
        tag.putDouble(TEMPERATURE_KEY, temperatureK);
        ListTag gases = new ListTag();
        for (Map.Entry<GasType, Double> entry : getGases().entrySet()) {
            if (entry.getValue() <= 1e-9) {
                continue;
            }
            CompoundTag gasTag = new CompoundTag();
            gasTag.putString(GAS_KEY, entry.getKey().getResourceLocation().toString());
            gasTag.putDouble(MASS_KEY, entry.getValue());
            gases.add(gasTag);
        }
        tag.put(GASES_KEY, gases);
    }

    private void readClientState(CompoundTag tag) {
        if (tag.contains(ENERGY_KEY)) {
            energyStorage.setEnergyStoredExact(tag.getInt(ENERGY_KEY));
        }
        if (tag.contains(TEMPERATURE_KEY)) {
            temperatureK = clampTemperature(tag.getDouble(TEMPERATURE_KEY));
        }
        Map<ResourceLocation, Double> gases = new LinkedHashMap<>();
        for (Tag element : tag.getList(GASES_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag gasTag = (CompoundTag) element;
            ResourceLocation id = ResourceLocation.tryParse(gasTag.getString(GAS_KEY));
            if (id != null) {
                gases.put(id, gasTag.getDouble(MASS_KEY));
            }
        }
        clientGasMasses = gases;
    }

    private static double clampTemperature(double value) {
        if (Double.isNaN(value)) {
            return AMBIENT_TEMPERATURE_K;
        }
        return Mth.clamp(value, MIN_TEMPERATURE_K, MAX_STORED_TEMPERATURE_K);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeClientState(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        readClientState(tag);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.@NotNull Provider registries) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag(), registries);
        }
    }

    // --- persistence ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_KEY, energyStorage.getEnergyStored());
        tag.putDouble(TEMPERATURE_KEY, temperatureK);
        tag.put(INVENTORY_KEY, inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergyStoredExact(tag.getInt(ENERGY_KEY));
        if (tag.contains(TEMPERATURE_KEY)) {
            temperatureK = clampTemperature(tag.getDouble(TEMPERATURE_KEY));
        }
        if (tag.contains(INVENTORY_KEY)) {
            inventory.deserializeNBT(registries, tag.getCompound(INVENTORY_KEY));
        }
        cachedRecipe = null;
    }

    // --- menu -------------------------------------------------------------------------------

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.vaporizer");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory,
                                                      @NotNull Player player) {
        return new VaporizerMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- inner types ------------------------------------------------------------------------

    private class VaporizerInventory extends ItemStackHandler {
        private VaporizerInventory() {
            super(SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return hasVaporizingRecipeAccepting(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            cachedRecipe = null;
        }
    }

    /** Delegating handler that lets automation fill the slots but never empty them. */
    private static class InsertOnlyHandler implements IItemHandler {
        private final IItemHandler delegate;

        private InsertOnlyHandler(IItemHandler delegate) {
            this.delegate = delegate;
        }

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
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
    }

    private static class VaporizerEnergyStorage extends EnergyStorage {
        private VaporizerEnergyStorage() {
            super(MAX_ENERGY, MAX_RECEIVE, 0);
        }

        private void consume(int amount) {
            energy = Math.max(0, energy - amount);
        }

        private void setEnergyStoredExact(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }
}
