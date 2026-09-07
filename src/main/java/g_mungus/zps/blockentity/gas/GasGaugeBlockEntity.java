package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.GasGaugeBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads one property of the gas at its node and maps it onto a configured range.
 *
 * <p>The gauge measures either pressure or temperature — {@link Mode} — between a lower and an upper
 * bound of the player's choosing. Everything downstream works from the normalised reading
 * ({@link #getFraction()}): the needle sweeps from one end of the dial to the other over it, and the
 * comparator signal runs from 0 at the lower bound to 15 at the upper. Readings outside the range
 * pin at the ends rather than wrapping or going dark.
 *
 * <p>Each mode keeps its own bounds, so switching from pressure to temperature and back does not
 * leave a Pascal range applied to Kelvin.
 *
 * <p>Nothing here moves gas. The node fills from the line it is bolted to and stays at the line's
 * pressure; the block only looks.
 */
public class GasGaugeBlockEntity extends GasNodeBlockEntity {

    /** Which property of the gas the dial reads. */
    public enum Mode implements StringRepresentable {
        /** Pressure at the node, in Pascals. */
        PRESSURE("pressure", "Pa", 0.0, GasGaugeBlock.MAX_PRESSURE, 1.0e12),
        /** Temperature at the node, in Kelvin. */
        TEMPERATURE("temperature", "K", 0.0, GasGaugeBlock.MAX_TEMPERATURE, 1.0e6);

        private final String serializedName;
        private final String unit;
        private final double defaultLower;
        private final double defaultUpper;
        /** The largest bound the mode accepts, so a stray packet cannot store infinity. */
        private final double ceiling;

        Mode(String serializedName, String unit, double defaultLower, double defaultUpper, double ceiling) {
            this.serializedName = serializedName;
            this.unit = unit;
            this.defaultLower = defaultLower;
            this.defaultUpper = defaultUpper;
            this.ceiling = ceiling;
        }

        @Override
        public @NotNull String getSerializedName() {
            return serializedName;
        }

        /** The unit symbol readings in this mode are shown in. */
        public String unit() {
            return unit;
        }

        public double defaultLower() {
            return defaultLower;
        }

        public double defaultUpper() {
            return defaultUpper;
        }

        public double ceiling() {
            return ceiling;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** Translation key of the mode's display name. */
        public String translationKey() {
            return "gui.zps.gas_gauge.mode." + serializedName;
        }

        /**
         * A reading in this mode, with a unit prefix chosen so the number stays short: pressures
         * on a working line run to megapascals, where a figure in plain Pascals is unreadable.
         */
        public String format(double value) {
            return switch (this) {
                case PRESSURE -> {
                    double magnitude = Math.abs(value);
                    if (magnitude >= 1.0e6) {
                        yield String.format(Locale.ROOT, "%.2f MPa", value / 1.0e6);
                    }
                    if (magnitude >= 1.0e3) {
                        yield String.format(Locale.ROOT, "%.1f kPa", value / 1.0e3);
                    }
                    yield String.format(Locale.ROOT, "%.0f Pa", value);
                }
                case TEMPERATURE -> String.format(Locale.ROOT, "%.1f K", value);
            };
        }

        public static Mode bySerializedName(@Nullable String name) {
            for (Mode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return PRESSURE;
        }
    }

    /** The span a mode maps its reading across. Always {@code lower < upper}. */
    public record Bounds(double lower, double upper) {
        public double fractionOf(double value) {
            return Mth.clamp((value - lower) / (upper - lower), 0.0, 1.0);
        }
    }

    private static final String MODE_KEY = "Mode";
    private static final String LOWER_KEY_SUFFIX = "Lower";
    private static final String UPPER_KEY_SUFFIX = "Upper";

    private Mode mode = Mode.PRESSURE;
    private final Map<Mode, Bounds> bounds = new EnumMap<>(Mode.class);

    private int lastComparatorOutput = -1;

    /** Where the needle was last drawn, as a fraction of the sweep. Client only. */
    private float clientNeedleFraction;

    public GasGaugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_GAUGE.get(), pos, state);
        for (Mode each : Mode.values()) {
            bounds.put(each, new Bounds(each.defaultLower(), each.defaultUpper()));
        }
    }

    // --- work -------------------------------------------------------------------------------

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // What feeds the needle and the screen's readout; without it the client sees nothing.
        syncNodeState();
        updateComparator();
    }

    private void updateComparator() {
        int output = getComparatorOutputSignal();
        if (output == lastComparatorOutput) {
            return;
        }
        lastComparatorOutput = output;
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // --- reading ----------------------------------------------------------------------------

    /** The measured property in the current mode's unit. Simulated on the server, synced on the client. */
    public double getMeasuredValue() {
        return mode == Mode.PRESSURE ? getPressure() : getTemperature();
    }

    /** The reading normalised over the current bounds, clamped to 0..1. */
    public double getFraction() {
        return getBounds().fractionOf(getMeasuredValue());
    }

    /** 0 at or below the lower bound, 15 at or above the upper, linear between. */
    public int getComparatorOutputSignal() {
        return (int) Math.round(getFraction() * 15.0);
    }

    // --- settings ---------------------------------------------------------------------------

    public Mode getMode() {
        return mode;
    }

    /** The bounds of the current mode. */
    public Bounds getBounds() {
        return getBounds(mode);
    }

    public Bounds getBounds(Mode ofMode) {
        return bounds.get(ofMode);
    }

    /**
     * Apply what the screen sent: the mode to read in and that mode's bounds. Bounds that are not
     * finite, negative, over the mode's ceiling, or not in order are ignored — the mode still
     * changes, but the old bounds stay rather than storing a range nothing can be mapped onto.
     *
     * @return whether the bounds were accepted
     */
    public boolean setSettings(Mode newMode, double lower, double upper) {
        this.mode = newMode;
        boolean accepted = acceptableBounds(newMode, lower, upper);
        if (accepted) {
            bounds.put(newMode, new Bounds(lower, upper));
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return accepted;
    }

    public static boolean acceptableBounds(Mode ofMode, double lower, double upper) {
        return Double.isFinite(lower) && Double.isFinite(upper)
                && lower >= 0.0 && upper <= ofMode.ceiling()
                && lower < upper;
    }

    // --- client -----------------------------------------------------------------------------

    public float getClientNeedleFraction() {
        return clientNeedleFraction;
    }

    public void setClientNeedleFraction(float clientNeedleFraction) {
        this.clientNeedleFraction = clientNeedleFraction;
    }

    // --- persistence ------------------------------------------------------------------------

    private void writeSettings(CompoundTag tag) {
        tag.putString(MODE_KEY, mode.getSerializedName());
        for (Map.Entry<Mode, Bounds> entry : bounds.entrySet()) {
            String prefix = entry.getKey().getSerializedName();
            tag.putDouble(prefix + LOWER_KEY_SUFFIX, entry.getValue().lower());
            tag.putDouble(prefix + UPPER_KEY_SUFFIX, entry.getValue().upper());
        }
    }

    private void readSettings(CompoundTag tag) {
        if (tag.contains(MODE_KEY)) {
            mode = Mode.bySerializedName(tag.getString(MODE_KEY));
        }
        for (Mode each : Mode.values()) {
            String prefix = each.getSerializedName();
            if (!tag.contains(prefix + LOWER_KEY_SUFFIX) || !tag.contains(prefix + UPPER_KEY_SUFFIX)) {
                continue;
            }
            double lower = tag.getDouble(prefix + LOWER_KEY_SUFFIX);
            double upper = tag.getDouble(prefix + UPPER_KEY_SUFFIX);
            if (acceptableBounds(each, lower, upper)) {
                bounds.put(each, new Bounds(lower, upper));
            }
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
