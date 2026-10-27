package dev.core.reward;

/**
 * Normalized position curve for the case reel.
 *
 * <p>
 * The curve is a cubic Bezier with control points {@code (0.30, 0.0)} and
 * {@code (0.50, 1.0)}. It keeps the reel near its top speed for the early part
 * of the roll, then the deceleration becomes increasingly noticeable until the
 * final crawl. The curve is normalized, so the configured top speed determines
 * actual slot speed independently of the roll duration.
 */
public final class CaseRollMath {

    private static final int SAMPLES = 4096;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double X1 = 0.30;
    private static final double Y1 = 0.0;
    private static final double X2 = 0.50;
    private static final double Y2 = 1.0;
    private static final double[] PROGRESS = new double[SAMPLES + 1];
    private static final double PEAK_SPEED;

    static {
        for (int i = 0; i <= SAMPLES; i++) {
            PROGRESS[i] = bezierY(solveBezierT(i / (double) SAMPLES));
        }
        PROGRESS[0] = 0.0;
        PROGRESS[SAMPLES] = 1.0;
        PEAK_SPEED = findPeakSpeed();
    }

    private CaseRollMath() {
    }

    /** Normalized reel position for a normalized elapsed time in [0, 1]. */
    public static double progress(double normalizedTime) {
        double p = clamp(normalizedTime);
        double scaled = p * SAMPLES;
        int lower = (int) Math.floor(scaled);
        if (lower >= SAMPLES) {
            return 1.0;
        }
        int upper = lower + 1;
        double fraction = scaled - lower;
        return PROGRESS[lower] + (PROGRESS[upper] - PROGRESS[lower]) * fraction;
    }

    /** Instantaneous speed of the curve at a normalized time. */
    public static double speedAt(double normalizedTime) {
        double t = solveBezierT(clamp(normalizedTime));
        double oneMinusT = 1.0 - t;
        double dx = 3.0 * oneMinusT * oneMinusT * X1
                + 6.0 * oneMinusT * t * (X2 - X1)
                + 3.0 * t * t * (1.0 - X2);
        double dy = 3.0 * oneMinusT * oneMinusT * Y1
                + 6.0 * oneMinusT * t * (Y2 - Y1)
                + 3.0 * t * t * (1.0 - Y2);
        if (dx <= 1.0e-12) {
            return 0.0;
        }
        return dy / dx;
    }

    /** Peak normalized speed of the curve. */
    public static double peakSpeed() {
        return PEAK_SPEED;
    }

    /**
     * Number of reel slots to travel so the curve reaches the requested top
     * speed over the given duration. The curve is normalized, so longer rolls
     * simply travel farther instead of spinning more slowly.
     */
    public static int maxOffsetForTopSpeed(int totalTicks, double topSlotsPerSecond) {
        if (totalTicks <= 0 || topSlotsPerSecond <= 0) {
            return 0;
        }
        double seconds = totalTicks / TICKS_PER_SECOND;
        return Math.max(1, (int) Math.round(topSlotsPerSecond * seconds / PEAK_SPEED));
    }

    /**
     * Continuous reel position for a tick. Vanilla chest GUIs should use
     * {@link #offsetAtTick(int, int, int)}, while a packet/display-entity based
     * renderer can consume this sub-slot value directly for interpolation.
     */
    public static double positionAtTick(int tick, int totalTicks, int maxOffset) {
        if (totalTicks <= 0 || maxOffset <= 0) {
            return 0.0;
        }
        double p = Math.min(1.0, Math.max(0.0, tick / (double) totalTicks));
        return progress(p) * maxOffset;
    }

    /**
     * Discrete reel index for a tick. Flooring on the continuous position means
     * the final index is not reached until the last tick, so the winning item
     * cannot appear in the center slot early and spoil the reveal.
     */
    public static int offsetAtTick(int tick, int totalTicks, int maxOffset) {
        if (totalTicks <= 0 || maxOffset <= 0) {
            return 0;
        }
        int offset = (int) Math.floor(positionAtTick(tick, totalTicks, maxOffset));
        return Math.min(maxOffset, Math.max(0, offset));
    }

    private static double findPeakSpeed() {
        double peak = 0.0;
        for (int i = 0; i <= SAMPLES; i++) {
            peak = Math.max(peak, speedAt(i / (double) SAMPLES));
        }
        return peak;
    }

    private static double bezierY(double t) {
        double oneMinusT = 1.0 - t;
        return 3.0 * oneMinusT * oneMinusT * t * Y1
                + 3.0 * oneMinusT * t * t * Y2
                + t * t * t;
    }

    private static double bezierX(double t) {
        double oneMinusT = 1.0 - t;
        return 3.0 * oneMinusT * oneMinusT * t * X1
                + 3.0 * oneMinusT * t * t * X2
                + t * t * t;
    }

    /**
     * Finds the Bezier parameter whose x-coordinate equals the given normalized
     * time. The x control points are monotonic, so bisection is stable.
     */
    private static double solveBezierT(double x) {
        double target = clamp(x);
        double low = 0.0;
        double high = 1.0;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) * 0.5;
            if (bezierX(mid) < target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
