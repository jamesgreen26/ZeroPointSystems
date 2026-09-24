package g_mungus.zps.util;

/**
 * A rolling per-tick average over a fixed window of game ticks. Samples are keyed by game time, so
 * ticks that never recorded anything count as zero and a block that stopped working decays to zero
 * on its own once the window has passed.
 */
public class TickAverage {

    private final int window;
    private final int[] samples;
    private long lastTick = Long.MIN_VALUE;

    public TickAverage(int window) {
        this.window = window;
        this.samples = new int[window];
    }

    /** Add to the sample for {@code gameTime}; call this once per contribution within a tick. */
    public void record(int value, long gameTime) {
        advanceTo(gameTime);
        samples[slot(gameTime)] += value;
    }

    /** Replace the sample for {@code gameTime} with a total already accumulated elsewhere. */
    public void set(int value, long gameTime) {
        advanceTo(gameTime);
        samples[slot(gameTime)] = value;
    }

    /** The average per tick over the window ending at {@code gameTime}. */
    public int average(long gameTime) {
        advanceTo(gameTime);
        long sum = 0;
        for (int sample : samples) {
            sum += sample;
        }
        return (int) (sum / window);
    }

    private void advanceTo(long gameTime) {
        if (lastTick == Long.MIN_VALUE) {
            lastTick = gameTime;
            return;
        }
        if (gameTime == lastTick) {
            return;
        }
        if (gameTime < lastTick || gameTime - lastTick >= window) {
            // Time jumped (or ran backwards) further than the window covers; nothing is still valid.
            java.util.Arrays.fill(samples, 0);
        } else {
            for (long tick = lastTick + 1; tick <= gameTime; tick++) {
                samples[slot(tick)] = 0;
            }
        }
        lastTick = gameTime;
    }

    private int slot(long gameTime) {
        return (int) Math.floorMod(gameTime, (long) window);
    }
}
