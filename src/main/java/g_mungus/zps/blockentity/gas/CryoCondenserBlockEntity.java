package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.CryoCondenserBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.menu.CryoCondenserMenu;
import g_mungus.zps.recipe.CondensingRecipe;
import g_mungus.zps.recipe.ModRecipes;
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
import net.minecraft.world.Clearable;
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
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Freezes gas into items according to {@link CondensingRecipe}s, paying for each craft in heat: the
 * Vaporizer run backwards.
 *
 * <p>The machine carries a temperature of its own, separate from the gas at its node. Each tick it
 * looks at which recipes have enough of their gas at the node, and burns FE to cool toward the
 * lowest of their maximum temperatures. Then each recipe gets at most one craft, provided the
 * machine is cold enough for it and its product fits in the output. A craft warms the machine by
 * the recipe's cost scaled by the gas's temperature, so hot gas is dearer to freeze than cool gas.
 *
 * <p>Recipes are tried in a fixed order, starting after the last one that crafted: a craft warms
 * the machine, which could otherwise leave a recipe later in the order always too warm to run.
 *
 * <p>Gas moves freely in and out of the node, and nothing here removes gas no recipe takes.
 */
public class CryoCondenserBlockEntity extends GasNodeBlockEntity implements MenuProvider, Clearable {
    public static final int SLOT_COUNT = 3;

    private static final int MAX_ENERGY = 8192;
    private static final int MAX_RECEIVE = 512;

    /** Where a freshly placed machine starts, and Kelvin's own ambient. */
    public static final double AMBIENT_TEMPERATURE_K = 273.15;
    /** The coldest the machine can get. */
    public static final double MIN_TEMPERATURE_K = 1.0;
    private static final double MAX_STORED_TEMPERATURE_K = 1.0e6;

    /** FE to cool the machine by one Kelvin, as the Vaporizer pays to heat by one. */
    public static final int FE_PER_KELVIN = VaporizerBlockEntity.FE_PER_KELVIN;
    /** The most FE the cooler draws in a tick, as the Vaporizer's heater. */
    public static final int MAX_COOL_FE_PER_TICK = VaporizerBlockEntity.MAX_HEAT_FE_PER_TICK;

    /** Below this, a gas reading is noise rather than gas, for the screen. */
    private static final double TRACE_KG = 1e-9;

    /** What the machine is doing this tick, for the screen. */
    public enum Status {
        IDLE,
        COOLING,
        NO_POWER,
        CONDENSING,
        OUTPUT_FULL;

        private static final Status[] VALUES = values();

        public static Status byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
        }
    }

    private static final String ENERGY_KEY = "Energy";
    private static final String TEMPERATURE_KEY = "Temperature";
    private static final String INVENTORY_KEY = "Inventory";
    private static final String LAST_CRAFTED_KEY = "LastCrafted";
    private static final String GASES_KEY = "Gases";
    private static final String GAS_KEY = "Gas";
    private static final String MASS_KEY = "Mass";

    private final CondenserEnergyStorage energyStorage = new CondenserEnergyStorage();
    private final CondenserInventory inventory = new CondenserInventory();
    /** Capability view: automation takes the products but never puts anything in. */
    private final IItemHandler automationHandler = new ExtractOnlyHandler(inventory);

    private double temperatureK = AMBIENT_TEMPERATURE_K;
    private Status status = Status.IDLE;
    /** The temperature the cooler is working toward, or zero when it has nothing to cool for. */
    private double targetTemperatureK;
    /** The recipe that crafted last, where the next tick's round starts after. */
    @Nullable
    private ResourceLocation lastCrafted;

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

    public CryoCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_CONDENSER.get(), pos, state);
    }

    // --- capabilities -----------------------------------------------------------------------

    /** Capability handler for hoppers and pipes: extract only. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return automationHandler;
    }

    /** The output slots themselves, for the GUI and tests. */
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

    /** The temperature the cooler is aiming for, or zero when idle. */
    public double getTargetTemperature() {
        return targetTemperatureK;
    }

    /** Client-side: what the tank holds, by gas id, in kilograms. */
    public Map<ResourceLocation, Double> getClientGasMasses() {
        return clientGasMasses;
    }

    /** The node's pressure ceiling, so the screen can draw the buffer's fill level. */
    public double getMaxPressure() {
        return CryoCondenserBlock.MAX_PRESSURE;
    }

    // --- work -------------------------------------------------------------------------------

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        restoreNodeIfMissing();

        List<RecipeHolder<CondensingRecipe>> ready = readyRecipes();
        if (ready.isEmpty()) {
            setStatus(Status.IDLE, 0);
        } else {
            double target = ready.stream().mapToDouble(holder -> holder.value().maxTemperature()).min().orElseThrow();
            Status cooling = coolToward(target);
            Status crafting = condense(ready);
            setStatus(crafting != null ? crafting : cooling, target);
        }

        if (syncNodeState()) {
            syncGasContentsIfChanged();
        }
    }

    /**
     * Every recipe with at least one craft's worth of its gas at the node, in the fixed order
     * recipes are tried in: by id.
     */
    private List<RecipeHolder<CondensingRecipe>> readyRecipes() {
        Map<GasType, Double> gases = getGases();
        List<RecipeHolder<CondensingRecipe>> ready = new ArrayList<>();
        if (gases.isEmpty()) {
            return ready;
        }
        for (RecipeHolder<CondensingRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.CONDENSING_TYPE.get())) {
            CondensingRecipe recipe = holder.value();
            GasType gas = recipe.ingredient().resolve();
            if (gas != null && gases.getOrDefault(gas, 0.0) >= recipe.ingredient().massKg()) {
                ready.add(holder);
            }
        }
        ready.sort(Comparator.comparing(RecipeHolder::id));
        return ready;
    }

    /**
     * Burn FE to close the gap down to {@code target}, at most {@link #MAX_COOL_FE_PER_TICK} a tick
     * and never past the target itself.
     *
     * @return what the cooler is doing, for the status when nothing crafts
     */
    private Status coolToward(double target) {
        double excessK = temperatureK - target;
        if (excessK <= 0) {
            return Status.COOLING;
        }
        double maxStepK = (double) MAX_COOL_FE_PER_TICK / FE_PER_KELVIN;
        int feWanted = (int) Math.ceil(Math.min(excessK, maxStepK) * FE_PER_KELVIN);
        int fe = Math.min(feWanted, energyStorage.getEnergyStored());
        if (fe <= 0) {
            return Status.NO_POWER;
        }
        energyStorage.consume(fe);
        temperatureK = Math.max(target, temperatureK - (double) fe / FE_PER_KELVIN);
        setChanged();
        return Status.COOLING;
    }

    /**
     * Give each ready recipe at most one craft, round robin from after the last one that crafted.
     *
     * @return {@link Status#CONDENSING} if anything crafted, {@link Status#OUTPUT_FULL} if a recipe
     *         was cold enough but its product had nowhere to go, else null
     */
    @Nullable
    private Status condense(List<RecipeHolder<CondensingRecipe>> ready) {
        // Read once: the gas the crafts take is part of the same body of gas, at the same temperature.
        double gasTemperatureK = getTemperature();
        boolean crafted = false;
        boolean blockedByOutput = false;
        for (RecipeHolder<CondensingRecipe> holder : roundRobinOrder(ready, lastCrafted)) {
            CondensingRecipe recipe = holder.value();
            if (temperatureK > recipe.maxTemperature()) {
                continue;
            }
            ItemStack product = recipe.result();
            if (!ItemHandlerHelper.insertItemStacked(inventory, product, true).isEmpty()) {
                blockedByOutput = true;
                continue;
            }
            GasType gas = recipe.ingredient().resolve();
            if (gas == null || !KelvinMod.INSTANCE.forceGetKelvin()
                    .removeGas(getDuctNodePosition(), gas, recipe.ingredient().massKg())) {
                continue;
            }
            ItemHandlerHelper.insertItemStacked(inventory, product, false);
            temperatureK = clampTemperature(temperatureK + recipe.costAt(gasTemperatureK));
            lastCrafted = holder.id();
            crafted = true;
            setChanged();
        }
        if (crafted) {
            return Status.CONDENSING;
        }
        return blockedByOutput ? Status.OUTPUT_FULL : null;
    }

    /**
     * {@code recipes}, already in their fixed order, rotated to start just after {@code lastCrafted}.
     * If that recipe is not among them, the round starts after where it would sort.
     */
    public static List<RecipeHolder<CondensingRecipe>> roundRobinOrder(List<RecipeHolder<CondensingRecipe>> recipes,
                                                                       @Nullable ResourceLocation lastCrafted) {
        if (lastCrafted == null || recipes.isEmpty()) {
            return recipes;
        }
        int start = 0;
        while (start < recipes.size() && recipes.get(start).id().compareTo(lastCrafted) <= 0) {
            start++;
        }
        List<RecipeHolder<CondensingRecipe>> ordered = new ArrayList<>(recipes.size());
        ordered.addAll(recipes.subList(start, recipes.size()));
        ordered.addAll(recipes.subList(0, start));
        return ordered;
    }

    private void setStatus(Status newStatus, double target) {
        status = newStatus;
        targetTemperatureK = target;
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

    @Override
    public void clearContent() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    // --- client sync ------------------------------------------------------------------------

    /** Push the tank's contents to clients when they have moved since the last push. */
    private void syncGasContentsIfChanged() {
        Map<ResourceLocation, Double> current = new HashMap<>();
        for (Map.Entry<GasType, Double> entry : getGases().entrySet()) {
            if (entry.getValue() > TRACE_KG) {
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
            if (entry.getValue() <= TRACE_KG) {
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
        if (lastCrafted != null) {
            tag.putString(LAST_CRAFTED_KEY, lastCrafted.toString());
        }
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
        lastCrafted = tag.contains(LAST_CRAFTED_KEY) ? ResourceLocation.tryParse(tag.getString(LAST_CRAFTED_KEY)) : null;
    }

    // --- menu -------------------------------------------------------------------------------

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.cryo_condenser");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory,
                                                      @NotNull Player player) {
        return new CryoCondenserMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- inner types ------------------------------------------------------------------------

    private class CondenserInventory extends ItemStackHandler {
        private CondenserInventory() {
            super(SLOT_COUNT);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }

    /** Delegating handler that lets automation empty the slots but never fill them. */
    private static class ExtractOnlyHandler implements IItemHandler {
        private final IItemHandler delegate;

        private ExtractOnlyHandler(IItemHandler delegate) {
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

    private static class CondenserEnergyStorage extends EnergyStorage {
        private CondenserEnergyStorage() {
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
