package g_mungus.zps.tractor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Tractor Beams running in each level, for anything that needs to ask "is this inside a beam?" without being
 * the beam: a player working out whether it is weightless, the client drawing beams and dragging particles.
 * <p>
 * A running controller signs in every tick and an entry that has not been heard from lapses by itself, so unloaded
 * chunks, broken panels and beams that switch off all clean up the same way without any of them having to say so.
 * <p>
 * The two sides keep separate books. In single player the server and client threads both run beams at once, and
 * each only ever touches its own.
 * <p>
 * A beam that has lapsed is not quite forgotten. It is no longer running, and nothing that asks what is running
 * will hear of it, but it is kept aside for as long as its glow takes to die away, so that the client can draw a
 * beam going out rather than one vanishing. See {@link Entry#glow} and {@link #visibleIn}.
 */
public final class RunningBeams {
    private static final int SCAN_INTERVAL = 5;
    private static final int LAPSE_TICKS = 3;

    /** Ticks a beam's glow takes to come up when it starts, and to die away when it stops. */
    private static final double GLOW_IN_TICKS = 8.0;
    private static final double GLOW_OUT_TICKS = 12.0;
    /** A beam that signed in less than this long ago is still running, as far as its glow is concerned. */
    private static final double RUNNING_WITHIN_TICKS = 2.5;
    /**
     * How the drawn length closes on the real one: by this fraction of what is left each tick, and never by less
     * than this many blocks a tick.
     */
    private static final double LENGTH_EASE = 0.25;
    private static final double LENGTH_MIN_PACE = 0.5;
    /** However a fading beam's glow is being tracked, it is dropped after this long. Nothing draws off screen. */
    private static final int FORGET_TICKS = 100;

    private static final Map<Level, Map<BlockPos, Entry>> SERVER = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Entry>> CLIENT = new WeakHashMap<>();
    /** Client only: beams that have stopped running and are still fading out. */
    private static final Map<Level, Map<BlockPos, Entry>> FADING = new WeakHashMap<>();

    private RunningBeams() {
    }

    public static final class Entry {
        private BeamGeometry geometry;
        private BeamScan scan;
        private AABB worldBounds;
        private long lastSeen;
        private long lastScan;
        private double glow;
        private double glowAsOf = Double.NaN;
        private double shownRange = Double.NaN;
        private double shownRangeAsOf = Double.NaN;
        /** A look down a longer beam than the real one, for drawing it while it shortens; see {@link #scanFor}. */
        private BeamScan longScan;
        private BeamGeometry longGeometry;
        private long longScanAt;

        public BeamGeometry geometry() {
            return geometry;
        }

        public BeamScan scan() {
            return scan;
        }

        /** A world-space box around the whole beam, as of the last tick. */
        public AABB worldBounds() {
            return worldBounds;
        }

        /**
         * How long the beam is drawn at {@code now} (game time, partial tick included). The beam's real range
         * changes in whole blocks, at once, whenever the redstone signal running it changes strength; the drawn
         * length follows it over about half a second, quickly at first and never slower than a set pace, so that
         * it always arrives. Moves on each time it is asked: once a frame.
         * <p>
         * A beam that has only just appeared is drawn at its full length straight away. Coming on and going off
         * are {@link #glow}'s business, and a beam that faded in while also growing would be doing two things.
         */
        public float shownRange(double now) {
            double target = geometry.range();
            double elapsed = Double.isNaN(shownRangeAsOf) ? 0.0 : Math.max(0.0, now - shownRangeAsOf);
            shownRangeAsOf = now;
            if (Double.isNaN(shownRange)) {
                shownRange = target;
            }
            double gap = target - shownRange;
            double step = Math.max(LENGTH_MIN_PACE, Math.abs(gap) * LENGTH_EASE) * elapsed;
            shownRange = Math.abs(gap) <= step ? target : shownRange + Math.signum(gap) * step;
            return (float) shownRange;
        }

        /**
         * What lies down the beam as far as it is being drawn, {@code shown} blocks. Usually that is the look
         * the beam works from. But a beam drawn longer than it really is, because it has just been shortened and
         * the drawn length is still coming down, has to end on whatever is out there past its real range, and its
         * own look stops at that range. For that stretch a second look is taken, at the longer length, and kept
         * for as long as it is needed.
         */
        public BeamScan scanFor(Level level, float shown) {
            int needed = (int) Math.ceil(shown - 1e-4);
            if (needed <= geometry.range()) {
                longScan = null;
                return scan;
            }
            long now = level.getGameTime();
            boolean fits = longScan != null && longGeometry.range() >= needed
                    && longGeometry.controller().equals(geometry.controller())
                    && longGeometry.facing() == geometry.facing() && longGeometry.width() == geometry.width();
            if (!fits || now - longScanAt >= SCAN_INTERVAL || now < longScanAt) {
                longGeometry = new BeamGeometry(geometry.controller(), geometry.facing(), geometry.width(), needed);
                longScan = BeamScan.scan(level, longGeometry);
                longScanAt = now;
            }
            return longScan;
        }

        /**
         * How brightly the beam shows at {@code now} (game time, partial tick included), from 0 to 1. It climbs
         * while the beam keeps signing in and falls once it stops, from wherever it had got to, so a beam switched
         * on and off faster than it can fade never jumps. Moves on each time it is asked: once a frame.
         */
        public float glow(double now) {
            double elapsed = Double.isNaN(glowAsOf) ? 0.0 : Math.max(0.0, now - glowAsOf);
            glowAsOf = now;
            // On the client a tick runs its block entities first and moves the clock on afterwards, so by the time
            // a frame is drawn a running beam's last sign-in reads as between one and two ticks old. The line has
            // to fall clear of that whole range: drawn inside it, every tick is half climbing and half falling.
            boolean running = now - lastSeen < RUNNING_WITHIN_TICKS;
            glow = running
                    ? Math.min(1.0, glow + elapsed / GLOW_IN_TICKS)
                    : Math.max(0.0, glow - elapsed / GLOW_OUT_TICKS);
            // Eased, so it neither snaps on nor cuts off.
            return (float) (glow * glow * (3.0 - 2.0 * glow));
        }
    }

    private static Map<Level, Map<BlockPos, Entry>> side(Level level) {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    /**
     * Signs a running beam in for this tick. A caller that looks down its own columns hands that over as
     * {@code scan}; with null the entry keeps a look of its own, refreshed every few ticks.
     */
    public static Entry signIn(Level level, BeamGeometry geometry, @Nullable BeamScan scan) {
        long now = level.getGameTime();
        Entry entry = side(level).computeIfAbsent(level, key -> new HashMap<>())
                .computeIfAbsent(geometry.controller(), pos -> {
                    // Switched back on before it had finished going out: carry on from the glow it still has.
                    Map<BlockPos, Entry> fading = level.isClientSide() ? FADING.get(level) : null;
                    Entry revived = fading == null ? null : fading.remove(pos);
                    return revived == null ? new Entry() : revived;
                });
        boolean changed = !geometry.equals(entry.geometry);
        entry.geometry = geometry;
        entry.worldBounds = geometry.worldBounds(level);
        entry.lastSeen = now;
        if (scan != null) {
            entry.scan = scan;
            entry.lastScan = now;
        } else if (changed || entry.scan == null || now - entry.lastScan >= SCAN_INTERVAL) {
            entry.scan = BeamScan.scan(level, geometry);
            entry.lastScan = now;
        }
        return entry;
    }

    /** The beams still running in {@code level} as of its current tick. */
    public static Collection<Entry> in(@Nullable Level level) {
        Map<BlockPos, Entry> beams = level == null ? null : side(level).get(level);
        if (beams == null || beams.isEmpty()) {
            return List.of();
        }
        long now = level.getGameTime();
        for (Iterator<Entry> it = beams.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (now - entry.lastSeen > LAPSE_TICKS || now < entry.lastSeen) {
                it.remove();
                if (level.isClientSide() && now >= entry.lastSeen) {
                    FADING.computeIfAbsent(level, key -> new HashMap<>()).put(entry.geometry.controller(), entry);
                }
            }
        }
        return beams.values();
    }

    /**
     * Client only: every beam there is something to draw of. The running ones, and the ones that have stopped but
     * whose glow has not yet died away. Ask each for its {@link Entry#glow}.
     */
    public static List<Entry> visibleIn(@Nullable Level level) {
        if (level == null) {
            return List.of();
        }
        List<Entry> visible = new ArrayList<>(in(level));
        Map<BlockPos, Entry> fading = FADING.get(level);
        if (fading != null) {
            long now = level.getGameTime();
            // Gone dark, or left behind by a camera that looked away and never saw it finish.
            fading.values().removeIf(entry -> entry.glow <= 0.0 || now - entry.lastSeen > FORGET_TICKS
                    || now < entry.lastSeen);
            visible.addAll(fading.values());
        }
        return visible;
    }

    /** Cheap enough to ask for every particle, every tick. May say no a few ticks late; never says yes wrongly. */
    public static boolean noneIn(@Nullable Level level) {
        Map<BlockPos, Entry> beams = level == null ? null : side(level).get(level);
        return beams == null || beams.isEmpty();
    }

    /** Whether any running beam has hold of {@code entity} right now. */
    public static boolean grips(Entity entity) {
        Level level = entity.level();
        if (noneIn(level) || !BeamForces.affects(entity)) {
            return false;
        }
        for (Entry entry : in(level)) {
            if (entry.worldBounds.intersects(entity.getBoundingBox())
                    && BeamForces.grip(level, entry.geometry, entry.scan, entity) != null) {
                return true;
            }
        }
        return false;
    }
}
