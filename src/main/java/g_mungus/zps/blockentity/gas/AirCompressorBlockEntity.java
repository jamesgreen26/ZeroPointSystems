package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.AirCompressorBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.menu.AirCompressorMenu;
import g_mungus.zps.room.RoomManager;
import g_mungus.zps.room.RoomScanner;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws a fixed volume of whatever is above its fan into its own node each tick, paying a
 * fixed FE for every tick it does.
 *
 * <p>The volume is fixed, not the mass, so what it moves follows the gas at the intake: a full
 * draw of sea-level air, less of the thin air high up, nothing at all in vacuum. Inside a sealed
 * room it draws the room's own mix and takes it out of the room ({@link RoomManager#drawGas}),
 * so the room thins as it runs and each tick brings in less than the last. Outdoors the atmosphere
 * never runs short. The gas keeps the temperature it had where it was drawn from.
 *
 * <p>It pauses, spending nothing, when the space above cannot hold air or has fluid in it, when
 * a redstone signal shuts it, and — like the Vaporizer — when one more tick's draw would take its
 * node past {@link #FULL_FRACTION} of the pressure ceiling. It starts again by itself as soon as
 * whatever stopped it clears.
 */
public class AirCompressorBlockEntity extends GasNodeBlockEntity implements MenuProvider {

    private static final int MAX_ENERGY = 8192;
    private static final int MAX_RECEIVE = 512;

    /** FE used each tick it draws gas in. Nothing while it is paused. */
    public static final int ENERGY_PER_TICK = 32;

    /** How full, against the node's pressure ceiling, it may get before it pauses. */
    public static final double FULL_FRACTION = 0.9;

    /** What the compressor did on its last tick. */
    public enum Status {
        RUNNING,
        SHUT,
        NO_POWER,
        BLOCKED,
        OUTPUT_FULL
    }

    private static final String ENERGY_KEY = "Energy";
    private static final String GASES_KEY = "Gases";
    private static final String GAS_KEY = "Gas";
    private static final String MASS_KEY = "Mass";

    private final AirCompressorEnergyStorage energyStorage = new AirCompressorEnergyStorage();
    private Status status = Status.NO_POWER;

    /** Client-side mirror of what is in the receiver, by gas id, for the screen's tooltip. */
    private Map<ResourceLocation, Double> clientGasMasses = new LinkedHashMap<>();
    /** What was last pushed to clients, so a block update only goes out when the contents move. */
    private Map<ResourceLocation, Double> lastSentGasMasses = new HashMap<>();

    /** Synced to the menu: energy and capacity. */
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergyStoredExact(value);
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public AirCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_COMPRESSOR.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    public Status getStatus() {
        return status;
    }

    /** Set the stored energy directly. For tests and commands. */
    public void setEnergyStored(int energy) {
        energyStorage.setEnergyStoredExact(energy);
        setChanged();
    }

    /** Client-side: what the receiver holds, by gas id, in kilograms. */
    public Map<ResourceLocation, Double> getClientGasMasses() {
        return clientGasMasses;
    }

    /** The node's pressure ceiling, so the screen can draw how full it is. */
    public double getMaxPressure() {
        return AirCompressorBlock.MAX_PRESSURE;
    }

    /** The space the compressor draws from: above its fan. */
    public BlockPos intakePos() {
        return worldPosition.relative(AirCompressorBlock.INTAKE);
    }

    // --- work -------------------------------------------------------------------------------

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        restoreNodeIfMissing();

        status = compress(serverLevel);
        boolean running = status == Status.RUNNING;
        BlockState state = getBlockState();
        if (state.getValue(AirCompressorBlock.RUNNING) != running) {
            serverLevel.setBlock(worldPosition, state.setValue(AirCompressorBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        }
        if (syncNodeState()) {
            syncGasContentsIfChanged();
        }
    }

    private Status compress(ServerLevel level) {
        if (AirCompressorBlock.isShut(getBlockState())) {
            return Status.SHUT;
        }
        if (energyStorage.getEnergyStored() < ENERGY_PER_TICK) {
            return Status.NO_POWER;
        }
        BlockPos intake = intakePos();
        if (!isOpen(level, intake)) {
            return Status.BLOCKED;
        }

        RoomManager rooms = RoomManager.get(level);
        double volume = ZPSConfig.airCompressorSweptVolume();
        RoomManager.Draw draw = rooms.peekGas(level, intake, volume);
        // In vacuum it still runs, and pays for it; there is just nothing for it to move.
        if (!draw.isEmpty()) {
            if (!hasRoomFor(draw.kg(), draw.temperatureK(), AirCompressorBlock.MAX_PRESSURE * FULL_FRACTION)) {
                return Status.OUTPUT_FULL;
            }
            draw = rooms.drawGas(level, intake, volume);
            DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
            DuctNodePos node = getDuctNodePosition();
            for (Map.Entry<GasType, Double> entry : draw.kg().entrySet()) {
                kelvin.addGasAtTemperature(node, entry.getKey(), entry.getValue(), draw.temperatureK());
            }
        }
        energyStorage.consume(ENERGY_PER_TICK);
        setChanged();
        return Status.RUNNING;
    }

    /**
     * Whether the space at {@code intake} is open to draw from: it holds some air, judged as rooms
     * judge it — so a grate or other air-permeable block counts as open — and has no fluid in it.
     */
    private static boolean isOpen(ServerLevel level, BlockPos intake) {
        BlockState state = level.getBlockState(intake);
        return level.getFluidState(intake).isEmpty()
                && RoomScanner.holdsAir(RoomScanner.shapeOf(level, intake, state));
    }

    // --- client sync ------------------------------------------------------------------------

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

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
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
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
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

    // --- menu -------------------------------------------------------------------------------

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.zps.air_compressor");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory,
                                                      @NotNull Player player) {
        return new AirCompressorMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- persistence ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_KEY, energyStorage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergyStoredExact(tag.getInt(ENERGY_KEY));
    }

    private static class AirCompressorEnergyStorage extends EnergyStorage {
        private AirCompressorEnergyStorage() {
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
