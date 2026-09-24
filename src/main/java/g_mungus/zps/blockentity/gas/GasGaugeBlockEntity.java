package g_mungus.zps.blockentity.gas;

import g_mungus.zps.block.gas.GasGaugeBlock;
import g_mungus.zps.blockentity.ModBlockEntities;
import g_mungus.zps.blockentity.gas.core.GasNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Reads one property of the gas at its node and maps it onto that property's range.
 *
 * <p>The gauge measures either pressure or temperature — {@link Mode} — over a fixed range: zero to
 * what a plain duct will stand. Everything downstream works from the normalised reading
 * ({@link #getFraction()}): the needle sweeps from one end of the dial to the other over it, and the
 * comparator signal runs from 0 at the bottom of the range to 15 at the top. Readings outside the
 * range pin at the ends rather than wrapping or going dark.
 *
 * <p>The mode is the only setting, cycled by a sneaking empty-handed click on the block; the
 * needle's colour says which one is showing.
 *
 * <p>Nothing here moves gas. The node fills from the line it is bolted to and stays at the line's
 * pressure; the block only looks.
 */
public class GasGaugeBlockEntity extends GasNodeBlockEntity {

    /** Which property of the gas the dial reads. */
    public enum Mode implements StringRepresentable {
        /** Pressure at the node, in Pascals. */
        PRESSURE("pressure", 0.0, GasGaugeBlock.MAX_PRESSURE),
        /** Temperature at the node, in Kelvin. */
        TEMPERATURE("temperature", 0.0, GasGaugeBlock.MAX_TEMPERATURE);

        private final String serializedName;
        private final Bounds bounds;

        Mode(String serializedName, double lower, double upper) {
            this.serializedName = serializedName;
            this.bounds = new Bounds(lower, upper);
        }

        @Override
        public @NotNull String getSerializedName() {
            return serializedName;
        }

        /** The span the dial covers in this mode. */
        public Bounds bounds() {
            return bounds;
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

    private Mode mode = Mode.PRESSURE;

    private int lastComparatorOutput = -1;

    /** Where the needle was last drawn, as a fraction of the sweep. Client only. */
    private float clientNeedleFraction;

    public GasGaugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_GAUGE.get(), pos, state);
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
        return mode.bounds();
    }

    /** Move on to the next mode, and tell clients so the needle changes colour. */
    public Mode cycleMode() {
        setMode(mode.next());
        return mode;
    }

    public void setMode(Mode newMode) {
        this.mode = newMode;
        setChanged();
        // The client learns the mode from the block update this sends, so a client-side call — a
        // ponder scene switching the dial — has nothing to send on.
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
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
    }

    /** Only the mode. Gauges saved when the bounds could be set still carry them; they are ignored. */
    private void readSettings(CompoundTag tag) {
        if (tag.contains(MODE_KEY)) {
            mode = Mode.bySerializedName(tag.getString(MODE_KEY));
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        writeSettings(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        readSettings(tag);
    }

    /** Only the settings go to clients; node state travels on {@code GasNodeSyncS2CPacket}. */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeSettings(tag);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        readSettings(tag);
    }
}
