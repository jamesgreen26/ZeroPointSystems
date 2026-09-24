package g_mungus.zps.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplayLayout;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import g_mungus.zps.config.ZPSConfig;
import g_mungus.zps.reactor.Reactor;
import g_mungus.zps.reactor.ReactorManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Create display link source for a fusion reactor, read through any Reinforced Plating in its
 * shell. Laid out like Create's own boiler status: a header row naming the reactor's state, then
 * a bar each for pressure, temperature and output.
 *
 * <p>The bars borrow the boiler's drawing: {@code |} characters coloured per segment on text
 * targets, block glyphs on a wide-flap "pixel" section for flap boards. Where the boiler's three
 * bars share one scale of heat levels, each of these is a fraction of its own limit: pressure of
 * the shell's burst pressure, temperature of the melt point, output of what the exchangers set up
 * to extract could deliver, averaged over the last few ticks.
 * Colour follows the boiler's reading too: dark green for what is reached, dark red for what is
 * still wanted, dark grey for the rest.
 */
public class ReactorDisplaySource extends DisplaySource {

    private static final String LANG = "zps.display_source.reactor.";

    /** Segments per bar, the same as the boiler's top heat level so the two read alike. */
    private static final int BAR_LENGTH = 18;

    /** Pressure past this fraction of burst is drawn in red. */
    private static final double PRESSURE_WARNING = 0.8;

    public static final List<MutableComponent> NOT_ENOUGH_SPACE_SINGLE =
            List.of(Component.translatable(LANG + "not_enough_space")
                    .append(Component.translatable(LANG + "for_reactor_status")));

    public static final List<MutableComponent> NOT_ENOUGH_SPACE_DOUBLE =
            List.of(Component.translatable(LANG + "not_enough_space"),
                    Component.translatable(LANG + "for_reactor_status"));

    public static final List<List<MutableComponent>> NOT_ENOUGH_SPACE_FLAP =
            List.of(List.of(Component.translatable(LANG + "not_enough_space")),
                    List.of(Component.translatable(LANG + "for_reactor_status")));

    private static final String[] ROW_LABELS = {"pressure", "temperature", "output"};

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (stats.maxRows() < 2)
            return NOT_ENOUGH_SPACE_SINGLE;
        else if (stats.maxRows() < 4)
            return NOT_ENOUGH_SPACE_DOUBLE;

        Stream<MutableComponent> rows = getComponents(context, false).map(ReactorDisplaySource::joinRow);

        if (context.getTargetBlockEntity() instanceof LecternBlockEntity) {
            return List.of(rows.reduce((a, b) -> a.append(Component.literal("\n")).append(b)).orElse(EMPTY_LINE));
        }
        return rows.toList();
    }

    @Override
    public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (stats.maxRows() < 4) {
            context.flapDisplayContext = Boolean.FALSE;
            return NOT_ENOUGH_SPACE_FLAP;
        }

        List<List<MutableComponent>> components = getComponents(context, true).toList();

        // No reactor behind this block (or not a server level): a single empty row, so there is
        // nothing to fit and the board keeps its default layout.
        if (components.size() < 4) {
            context.flapDisplayContext = Boolean.FALSE;
            return components;
        }

        // Same fit test as the boiler: the label column in regular flaps, the bar in wide ones.
        if (stats.maxColumns() * FlapDisplaySection.MONOSPACE < labelWidth() * FlapDisplaySection.MONOSPACE
                + components.get(1).get(1).getString().length() * FlapDisplaySection.WIDE_MONOSPACE) {
            context.flapDisplayContext = Boolean.FALSE;
            return NOT_ENOUGH_SPACE_FLAP;
        }

        return components;
    }

    @Override
    public void loadFlapDisplayLayout(DisplayLinkContext context, FlapDisplayBlockEntity flapDisplay,
                                      FlapDisplayLayout layout, int lineIndex) {
        if (lineIndex == 0 || context.flapDisplayContext instanceof Boolean b && !b) {
            if (layout.isLayout("Default"))
                return;

            layout.loadDefault(flapDisplay.getMaxCharCount());
            return;
        }

        String layoutKey = "ZPSReactor";
        if (layout.isLayout(layoutKey))
            return;

        int labelLength = (int) (labelWidth() * FlapDisplaySection.MONOSPACE);
        float maxSpace = flapDisplay.getMaxCharCount(1) * FlapDisplaySection.MONOSPACE;
        FlapDisplaySection label = new FlapDisplaySection(labelLength, "alphabet", false, true);
        FlapDisplaySection symbols = new FlapDisplaySection(maxSpace - labelLength, "pixel", false, false).wideFlaps();

        layout.configure(layoutKey, List.of(label, symbols));
    }

    @Override
    public int getPassiveRefreshTicks() {
        // A chamber moves a lot faster than a boiler; refresh once a second rather than every five.
        return 20;
    }

    private Stream<List<MutableComponent>> getComponents(DisplayLinkContext context, boolean forFlapDisplay) {
        if (!(context.level() instanceof ServerLevel level))
            return Stream.of(EMPTY);

        ReactorManager manager = ReactorManager.get(level);
        Reactor reactor = firstReactorAt(manager, context.getSourcePos());
        if (reactor == null)
            return Stream.of(EMPTY);

        ReactorManager.ChamberReading reading = manager.reading(level, reactor);

        String header = forFlapDisplay ? "status" : "status_short";
        MutableComponent status = statusOf(reactor, reading);

        double melt = ZPSConfig.reactorMeltTemperatureK();
        double pressure = reading == null ? 0 : reading.pressurePa() / reactor.burstPressure();
        double temperature = reading == null ? 0 : reading.temperatureK() / melt;
        double ignition = ZPSConfig.reactorIgnitionTemperatureK() / melt;
        int capacity = reactor.outputCapacityFePerTick(level);
        double output = capacity <= 0 ? 0 : (double) reactor.feOutAverage(level.getGameTime()) / capacity;

        return Stream.of(
                List.of(Component.translatable(LANG + header, status)),
                row("pressure", pressureBar(pressure, forFlapDisplay), forFlapDisplay),
                row("temperature", temperatureBar(temperature, ignition, forFlapDisplay), forFlapDisplay),
                row("output", outputBar(output, forFlapDisplay), forFlapDisplay));
    }

    // --- bars --------------------------------------------------------------------------------

    /** Filled to the fraction of burst pressure. Past the warning line the fill goes red. */
    private static MutableComponent pressureBar(double fraction, boolean forFlapDisplay) {
        int level = segments(fraction);
        if (forFlapDisplay)
            return blocks(level, BAR_LENGTH - level, 0);

        int warning = segments(PRESSURE_WARNING);
        int safe = Math.min(level, warning);
        return Component.empty()
                .append(bars(safe, ChatFormatting.DARK_GREEN))
                .append(bars(level - safe, ChatFormatting.RED))
                .append(bars(BAR_LENGTH - level, ChatFormatting.DARK_GRAY));
    }

    /**
     * Filled to the fraction of the melt point. The ignition segment is the boiler's green marker:
     * the part still to climb before it is drawn dark red, as the boiler draws unreached heat.
     */
    private static MutableComponent temperatureBar(double fraction, double ignitionFraction, boolean forFlapDisplay) {
        int level = segments(fraction);
        int ignition = Math.max(1, segments(ignitionFraction));
        if (forFlapDisplay) {
            int wanted = Math.max(0, ignition - level);
            return blocks(level, BAR_LENGTH - level - wanted, wanted);
        }

        int reached = Math.min(level, ignition - 1);
        return Component.empty()
                .append(bars(reached, ChatFormatting.DARK_GREEN))
                .append(bars(level >= ignition ? 1 : 0, ChatFormatting.GREEN))
                .append(bars(Math.max(0, level - ignition), ChatFormatting.DARK_GREEN))
                .append(bars(Math.max(0, ignition - level), ChatFormatting.DARK_RED))
                .append(bars(BAR_LENGTH - Math.max(level, ignition), ChatFormatting.DARK_GRAY));
    }

    /** Filled to the share of output capacity in use. */
    private static MutableComponent outputBar(double fraction, boolean forFlapDisplay) {
        int level = segments(fraction);
        if (forFlapDisplay)
            return blocks(level, BAR_LENGTH - level, 0);

        return Component.empty()
                .append(bars(level, ChatFormatting.DARK_GREEN))
                .append(bars(BAR_LENGTH - level, ChatFormatting.DARK_GRAY));
    }

    private static int segments(double fraction) {
        return Mth.clamp((int) Math.round(fraction * BAR_LENGTH), 0, BAR_LENGTH);
    }

    /** Text-target bar segments, as the boiler draws them. */
    private static MutableComponent bars(int count, ChatFormatting format) {
        return Component.literal("|".repeat(Math.max(0, count))).withStyle(format);
    }

    /** Flap-board bar: solid for what is reached, hatched for what is wanted, light for the rest. */
    private static MutableComponent blocks(int filled, int empty, int wanted) {
        return Component.literal("█".repeat(Math.max(0, filled))
                + "▒".repeat(Math.max(0, wanted))
                + "░".repeat(Math.max(0, empty)));
    }

    // --- lookup and labels ---------------------------------------------------------------------

    /**
     * Any reactor this wall block is part of. A single plating block can sit in the shell of two
     * neighbouring cavities; the first one the manager lists wins.
     */
    private static @Nullable Reactor firstReactorAt(ReactorManager manager, BlockPos pos) {
        List<Reactor> reactors = manager.reactorsAt(pos);
        for (Reactor reactor : reactors) {
            if (reactor.isWall(pos)) {
                return reactor;
            }
        }
        return null;
    }

    private static MutableComponent statusOf(Reactor reactor, ReactorManager.@Nullable ChamberReading reading) {
        String key;
        if (reading == null) {
            key = "offline";
        } else if (reactor.isLit()) {
            key = "lit";
        } else if (reactor.isEmpty()) {
            key = "empty";
        } else {
            key = "cold";
        }
        return Component.translatable(LANG + key);
    }

    private static List<MutableComponent> row(String label, MutableComponent bar, boolean forFlapDisplay) {
        MutableComponent labelComponent = labelOf(label);
        if (forFlapDisplay) {
            // Flap boards get a fixed-width label column: pad to the widest label.
            labelComponent = Component.literal(" ".repeat(labelWidth() - labelWidthOf(label))).append(labelComponent);
        } else {
            labelComponent = labelComponent.withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable(LANG + label + "_dots").withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of(labelComponent, bar);
    }

    private static MutableComponent joinRow(List<MutableComponent> components) {
        Optional<MutableComponent> reduce = components.stream().reduce(MutableComponent::append);
        return reduce.orElse(EMPTY_LINE);
    }

    private static int labelWidth() {
        int width = 0;
        for (String label : ROW_LABELS) {
            width = Math.max(width, labelWidthOf(label));
        }
        return width;
    }

    private static int labelWidthOf(String label) {
        return labelOf(label).getString().length();
    }

    private static MutableComponent labelOf(String label) {
        return Component.translatable(LANG + label);
    }

    @Override
    protected String getTranslationKey() {
        return "reactor_status";
    }
}
