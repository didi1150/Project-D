package dev.bukkit.hud;

/**
 * Pure decision helpers for real-time HUD tracking and fade-out animation.
 * Kept side-effect free so they can be unit tested without a server.
 */
final class HudTracking {

    private HudTracking() {}

    /**
     * How a position update should be applied to a display entity.
     */
    enum Move {
        /**
         * Below {@code min-step}: leave the display alone. Teleporting every
         * tick restarts the client-side interpolation and causes micro-stutter
         * while standing still or moving slowly.
         */
        SKIP,
        /** Normal per-tick follow using the configured teleport duration. */
        SMOOTH,
        /**
         * Above {@code snap-distance}: the player teleported or the wall
         * raytrace jumped. Hard-place immediately instead of gliding across
         * the world.
         */
        SNAP
    }

    /**
     * Classifies a pending position delta between the display and its target.
     *
     * @param distSq       squared distance from current display position to target
     * @param minStep      smallest delta worth sending (blocks)
     * @param snapDistance delta beyond which the movement is treated as a teleport
     */
    static Move decideMove(double distSq, double minStep, double snapDistance) {
        if (snapDistance > 0 && distSq > snapDistance * snapDistance) {
            return Move.SNAP;
        }
        if (minStep > 0 && distSq < minStep * minStep) {
            return Move.SKIP;
        }
        return Move.SMOOTH;
    }

    /**
     * Predictive lead offset along the player's velocity: the server observes
     * a moving player a couple of ticks late (network + client prediction), so
     * anchoring exactly at the observed position makes any interpolated
     * display trail behind. Leading by {@code velocity * leadTicks} cancels
     * most of the visible lag during straight-line motion.
     *
     * @return velocity scaled by leadTicks, clamped to {@code maxOffset} blocks
     */
    static org.bukkit.util.Vector lead(org.bukkit.util.Vector velocity, double leadTicks, double maxOffset) {
        org.bukkit.util.Vector out = velocity.clone().multiply(leadTicks);
        double len = out.length();
        if (len > maxOffset) {
            out.multiply(maxOffset / len);
        }
        return out;
    }

    /**
     * Fade progress for an expiring transient line: 1.0 while outside the
     * fade window, linearly down to 0.0 at expiry.
     *
     * @param remainingMs ms until expiry
     * @param fadeMs      length of the fade window
     */
    static float fadeFraction(long remainingMs, long fadeMs) {
        if (fadeMs <= 0) {
            return 1f;
        }
        if (remainingMs >= fadeMs) {
            return 1f;
        }
        if (remainingMs <= 0) {
            return 0f;
        }
        return (float) ((double) remainingMs / (double) fadeMs);
    }

    /** Fully opaque text-opacity byte as expected by TextDisplay#setTextOpacity. */
    static final int OPAQUE = 255;

    /**
     * Text opacity byte (0-255) for a fading fragment; {@link #OPAQUE} when no
     * fade window is active yet.
     */
    static int fadeOpacityByte(long remainingMs, long fadeMs) {
        float fraction = fadeFraction(remainingMs, fadeMs);
        return Math.round(fraction * OPAQUE);
    }
}
