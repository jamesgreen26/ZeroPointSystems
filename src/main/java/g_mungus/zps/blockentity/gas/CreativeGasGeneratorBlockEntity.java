package g_mungus.zps.blockentity.gas;

import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import g_mungus.zps.gas.ModGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.kelvin.KelvinMod;
import org.valkyrienskies.kelvin.api.GasType;
import org.valkyrienskies.kelvin.impl.registry.GasTypeRegistry;

/**
 * Pushes a fixed mass of a chosen gas into its own node every tick, at a chosen temperature.
 *
 * <p>Nothing throttles it: Kelvin's {@code addGasAtTemperature} adds whatever it is handed, and the
 * block's node has no pressure ceiling to rupture at, so the only limit is what the ducts hanging
 * off it can carry. That is the point — it is the knob you turn to find out.
 *
 * <p>A rate of zero is the off switch; there is no separate one.
 */
public class CreativeGasGeneratorBlockEntity extends GasNodeBlockEntity {

    /** The most gas the block will emit per tick, in kilograms. */
    public static final double MAX_RATE = 10.0;
    /** The hottest gas it will emit, in Kelvin. */
    public static final double MAX_TEMPERATURE = 3000.0;
    /** The coldest — absolute zero would divide by nothing in the solver. */
    public static final double MIN_TEMPERATURE = 1.0;

    /**
     * A vaporizer running flat out makes about this much per tick. Anything much higher bursts a
     * plain duct within seconds, which is a thing this block should let you do on purpose, not by
     * default the moment it is placed.
     */
    private static final double DEFAULT_RATE = 0.0025;
    private static final double DEFAULT_TEMPERATURE = 300.0;

    private static final String GAS_KEY = "Gas";
    private static final String RATE_KEY = "Rate";
    private static final String TEMPERATURE_KEY = "Temperature";

    private ResourceLocation gasId = ModGases.FLUX.getResourceLocation();
    private double rateKgPerTick = DEFAULT_RATE;
    private double temperatureK = DEFAULT_TEMPERATURE;

    public CreativeGasGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_GAS_GENERATOR.get(), pos, state);
    }

    // --- work -------------------------------------------------------------------------------

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (rateKgPerTick > 0) {
            GasType gas = GasTypeRegistry.INSTANCE.getGasType(gasId);
            if (gas != null) {
                KelvinMod.INSTANCE.forceGetKelvin().addGasAtTemperature(
                        getDuctNodePosition(), gas, rateKgPerTick, temperatureK);
            }
        }

        // What feeds the screen's live readout; without it the client sees nothing at all.
        syncNodeState();
    }

    // --- settings ---------------------------------------------------------------------------

    public ResourceLocation getGasId() {
        return gasId;
    }

    public double getRate() {
        return rateKgPerTick;
    }

    public double getEmissionTemperature() {
        return temperatureK;
    }

    /**
     * Apply what the screen sent. An unknown gas is ignored rather than stored, so a stale or
     * hostile packet cannot silently switch the block off by naming something that does not exist.
     */
    public void setSettings(ResourceLocation gas, double rate, double temperature) {
        if (GasTypeRegistry.INSTANCE.getGasType(gas) != null) {
            this.gasId = gas;
        }
        this.rateKgPerTick = Mth.clamp(rate, 0.0, MAX_RATE);
        this.temperatureK = Mth.clamp(temperature, MIN_TEMPERATURE, MAX_TEMPERATURE);

        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // --- persistence ------------------------------------------------------------------------

    private void writeSettings(CompoundTag tag) {
        tag.putString(GAS_KEY, gasId.toString());
        tag.putDouble(RATE_KEY, rateKgPerTick);
        tag.putDouble(TEMPERATURE_KEY, temperatureK);
    }

    private void readSettings(CompoundTag tag) {
        if (tag.contains(GAS_KEY)) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(GAS_KEY));
            if (parsed != null) {
                gasId = parsed;
            }
        }
        if (tag.contains(RATE_KEY)) {
            rateKgPerTick = Mth.clamp(tag.getDouble(RATE_KEY), 0.0, MAX_RATE);
        }
        if (tag.contains(TEMPERATURE_KEY)) {
            temperatureK = Mth.clamp(tag.getDouble(TEMPERATURE_KEY), MIN_TEMPERATURE, MAX_TEMPERATURE);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        writeSettings(tag);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        readSettings(tag);
    }

    /** Only the settings go to clients; node state travels on {@code GasNodeSyncS2CPacket}. */
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeSettings(tag);
        return tag;
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

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        readSettings(tag);
    }
}
