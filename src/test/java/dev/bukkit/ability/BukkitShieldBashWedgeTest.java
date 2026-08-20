package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Pure-math tests for the Shield Bash half-circle geometry. Neither touches
 * Bukkit, so they run headless.
 */
class BukkitShieldBashWedgeTest {

    private static final double RANGE = BukkitShieldBashEffect.BASH_RANGE;

    // ---- Half-circle geometry -------------------------------------------------

    @Test
    void straightAheadInsideSemicircle() {
        assertTrue(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), new Vector(0, 0, -2), RANGE));
    }

    @Test
    void straightAheadExactlyAtRangeIsInside() {
        assertTrue(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), new Vector(0, 0, -RANGE), RANGE));
    }

    @Test
    void beyondRangeIsOutside() {
        assertFalse(
                BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), new Vector(0, 0, -RANGE - 0.5), RANGE));
    }

    @Test
    void directlyToTheSideIsInside() {
        // Exactly 90 degrees off the forward axis is the half-circle's boundary.
        assertTrue(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), new Vector(2, 0, 0), RANGE));
    }

    @Test
    void justBehindTheSideIsOutside() {
        // A hair past perpendicular is already behind the caster.
        Vector offset = new Vector(2, 0, 0.1);
        assertFalse(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), offset, RANGE));
    }

    @Test
    void standingOnTheCasterIsInside() {
        assertTrue(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), new Vector(0, 0, 0), RANGE));
    }

    @Test
    void directlyBehindIsOutside() {
        assertFalse(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), new Vector(0, 0, 1.5), RANGE));
    }

    @Test
    void verticalComponentIsIgnored() {
        // The half-circle is horizontal: a flying high-up target's Y must not matter.
        assertTrue(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(0, 0, -1), new Vector(0, 99, -2), RANGE));
    }

    @Test
    void facingEastHitsEastSide() {
        assertTrue(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(1, 0, 0), new Vector(2, 0, 0.5), RANGE));
        assertFalse(BukkitShieldBashEffect.inFrontHalfCircle(new Vector(1, 0, 0), new Vector(-2, 0, 0.5), RANGE));
    }
}