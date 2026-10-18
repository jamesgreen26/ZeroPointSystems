package g_mungus.zps.blockentity.gas;

import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import g_mungus.zps.block.gas.AirCompressorBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.room.RoomManager;
import g_mungus.zps.room.RoomScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.DuctNetwork;
import org.valkyrienskies.kelvin.api.DuctNode;
import org.valkyrienskies.kelvin.api.DuctNodePos;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.util.GasPhysics;

import java.util.HashMap;
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
public class AirCompressorBlockEntity extends GasNodeBlockEntity {

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
        NOTHING_TO_DRAW,
        OUTPUT_FULL
    }

    private static final String ENERGY_KEY = "Energy";

    private final AirCompressorEnergyStorage energyStorage = new AirCompressorEnergyStorage();
    private Status status = Status.NO_POWER;

    public AirCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_COMPRESSOR.get(), pos, state);
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyStorage);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    public Status getStatus() {
        return status;
    }

    /** Set the stored energy directly. For tests and commands. */
    public void setEnergyStored(int energy) {
        energyStorage.setEnergyStoredExact(energy);
        setChanged();
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
        syncNodeState();
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
        if (draw.isEmpty()) {
            return Status.NOTHING_TO_DRAW;
        }
        if (!hasRoomFor(draw)) {
            return Status.OUTPUT_FULL;
        }

        draw = rooms.drawGas(level, intake, volume);
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos node = getDuctNodePosition();
        for (Map.Entry<GasType, Double> entry : draw.kg().entrySet()) {
            kelvin.addGasAtTemperature(node, entry.getKey(), entry.getValue(), draw.temperatureK());
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

    /**
     * Whether the node can take this draw without crossing {@link #FULL_FRACTION} of its pressure
     * ceiling. Predicted from the ideal gas law on what would be in it afterwards, at the hotter of
     * the node and the incoming gas — conservative, as the Vaporizer's is.
     */
    private boolean hasRoomFor(RoomManager.Draw draw) {
        DuctNetwork<?> kelvin = KelvinMod.INSTANCE.forceGetKelvin();
        DuctNodePos nodePos = getDuctNodePosition();
        DuctNode node = kelvin.getNodeAt(nodePos);
        if (node == null) {
            return false;
        }
        Map<GasType, Double> after = new HashMap<>(kelvin.getGasMassAt(nodePos));
        draw.kg().forEach((gas, mass) -> after.merge(gas, mass, Double::sum));
        double temperature = Math.max(kelvin.getTemperatureAt(nodePos), draw.temperatureK());
        double predicted = GasPhysics.INSTANCE.calcPressureFromGamma(after, node.getVolume(), temperature);
        return predicted < node.getMaxPressure() * FULL_FRACTION;
    }

    // --- persistence ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(ENERGY_KEY, energyStorage.getEnergyStored());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
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
