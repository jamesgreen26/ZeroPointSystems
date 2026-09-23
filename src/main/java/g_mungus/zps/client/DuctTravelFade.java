package g_mungus.zps.client;

import g_mungus.zps.entity.DuctTravelEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The black between one vent and the next.
 *
 * <p>A hop is a cut across arbitrary distance, and cutting straight to it reads as a glitch. Fading
 * through black covers three things at once: the jump itself, however long the server takes to
 * answer, and — because nothing else may be requested until the fade has run its course — how often
 * a player can move at all.
 *
 * <p>Drawn under the HUD rather than over it: the black covers the world, and the hotbar, health,
 * hunger and the crawl's own progress bar stay in view across it, as on any other ride.
 *
 * <p>The client starts fading the moment the key is pressed, so the response is immediate, while
 * the server independently holds the move back by {@link DuctTravelEntity#FADE_OUT_TICKS}. Since
 * the server counts from when the request reached it, the move always lands at or after the screen
 * is fully black, whatever the latency. Coming back is driven by the arriving state packet rather
 * than a timer, so a slow connection holds the black a little longer instead of flashing the old
 * room.
 */
public final class DuctTravelFade implements LayeredDraw.Layer {

    public static final DuctTravelFade INSTANCE = new DuctTravelFade();

    /**
     * How long the screen will wait in the dark for a hop that never lands before giving up. Has
     * to outlast the longest legitimate crawl, or the fade would give up on a journey still in
     * progress and let the player see themselves arrive.
     */
    private static final int BLACK_TIMEOUT = DuctTravelEntity.MAX_TRAVEL_TICKS + 60;

    private enum Phase {
        IDLE,
        OUT,
        BLACK,
        IN
    }

    private static Phase phase = Phase.IDLE;
    private static int ticks;
    /** Set when the hop lands before the fade-out has finished, so the black is not cut short. */
    private static boolean arrivalPending;

    private DuctTravelFade() {
    }

    /** The player has asked for another vent. */
    public static void beginHop() {
        phase = Phase.OUT;
        ticks = 0;
        arrivalPending = false;
    }

    /** The server has moved us. Start coming back. */
    public static void arrived() {
        switch (phase) {
            case BLACK -> {
                phase = Phase.IN;
                ticks = 0;
            }
            // Beat the fade out. Let it finish and skip the hold rather than flashing back early.
            case OUT -> arrivalPending = true;
            default -> {
            }
        }
    }

    public static void reset() {
        phase = Phase.IDLE;
        ticks = 0;
        arrivalPending = false;
    }

    /** Whether a hop is under way, and so whether another may be asked for. */
    public static boolean isTransitioning() {
        return phase != Phase.IDLE;
    }

    /**
     * Whether the player is on their way: the screen going dark or held there, and the server not
     * yet heard from. This is the stretch the journey's silence covers. Sound returns on arrival,
     * with the picture — or a beat ahead of it, if the hop lands while the screen is still
     * darkening and the fade is left to finish.
     */
    public static boolean isUnderway() {
        return phase == Phase.BLACK || (phase == Phase.OUT && !arrivalPending);
    }

    /**
     * Ticks the screen has been fully dark, or 0 when it is not. The black lasts as long as the
     * crawl between the two vents does, so this is how far along the journey is.
     */
    public static int darkTicks() {
        return phase == Phase.BLACK ? ticks : 0;
    }

    public static void tick() {
        ticks++;
        switch (phase) {
            case OUT -> {
                if (ticks >= DuctTravelEntity.FADE_OUT_TICKS) {
                    phase = arrivalPending ? Phase.IN : Phase.BLACK;
                    arrivalPending = false;
                    ticks = 0;
                }
            }
            case BLACK -> {
                if (ticks >= BLACK_TIMEOUT) {
                    phase = Phase.IN;
                    ticks = 0;
                }
            }
            case IN -> {
                if (ticks >= DuctTravelEntity.FADE_IN_TICKS) {
                    reset();
                }
            }
            case IDLE -> ticks = 0;
        }
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (phase == Phase.IDLE) {
            return;
        }
        // Whatever ended the ride — sneaking out, the vent being broken, leaving the world mid-hop
        // — ends the fade with it, so a stale phase can never black out a screen nobody is venting
        // on.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof DuctTravelEntity)) {
            reset();
            return;
        }

        float elapsed = ticks + deltaTracker.getGameTimeDeltaPartialTick(false);
        float opacity = switch (phase) {
            case OUT -> elapsed / DuctTravelEntity.FADE_OUT_TICKS;
            case BLACK -> 1.0f;
            case IN -> 1.0f - elapsed / DuctTravelEntity.FADE_IN_TICKS;
            case IDLE -> 0.0f;
        };

        int alpha = Mth.clamp(Math.round(opacity * 255.0f), 0, 255);
        if (alpha > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
        }
    }
}
