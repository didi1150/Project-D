package dev.bukkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import dev.bukkit.hud.HudTracking.Move;

class HudTrackingTest {

    private static final double EPS = 1e-9;

    @Test
    void belowMinStepSkips() {
        // 0.004 block min-step -> squared threshold
        assertEquals(Move.SKIP, HudTracking.decideMove(0.001 * 0.001, 0.004, 3.0));
        // exactly at the boundary is no longer skipped (strictly less-than)
        double at = 0.004 * 0.004;
        assertEquals(Move.SMOOTH, HudTracking.decideMove(at, 0.004, 3.0));
    }

    @Test
    void normalMovementSmooths() {
        assertEquals(Move.SMOOTH, HudTracking.decideMove(0.1 * 0.1, 0.004, 3.0));
    }

    @Test
    void beyondSnapDistanceSnaps() {
        assertEquals(Move.SNAP, HudTracking.decideMove(5 * 5, 0.004, 3.0));
        // exactly at the snap boundary is not yet a snap (strictly greater-than)
        double at = 3.0 * 3.0;
        assertEquals(Move.SMOOTH, HudTracking.decideMove(at, 0.004, 3.0));
    }

    @Test
    void zeroThresholdsNeverSkipOrSnap() {
        assertEquals(Move.SMOOTH, HudTracking.decideMove(0.0, 0.0, 0.25));
    }

    @Test
    void leadScalesVelocity() {
        Vector v = new Vector(0.28, 0, 0);
        Vector led = HudTracking.lead(v, 2.0, 1.0);
        assertEquals(0.56, led.getX(), EPS);
        assertEquals(0.0, led.getY(), EPS);
        // original vector untouched
        assertEquals(0.28, v.getX(), EPS);
    }

    @Test
    void leadClampsToMaxOffset() {
        Vector fast = new Vector(30, 40, 0); // length 50
        Vector led = HudTracking.lead(fast, 2.0, 1.0);
        assertEquals(1.0, led.length(), EPS);
        // direction preserved
        assertTrue(led.getX() > 0 && led.getY() > 0 && led.getZ() == 0);
    }

    @Test
    void fadeFractionOutsideWindowIsOpaque() {
        assertEquals(1f, HudTracking.fadeFraction(10_000, 600));
        assertEquals(1f, HudTracking.fadeFraction(Long.MAX_VALUE - 1, 600), "persistent fragments stay opaque");
    }

    @Test
    void fadeFractionLinearInsideWindow() {
        assertEquals(0.5f, HudTracking.fadeFraction(300, 600), 1e-6);
        assertEquals(0f, HudTracking.fadeFraction(0, 600));
        assertEquals(0f, HudTracking.fadeFraction(-50, 600));
    }

    @Test
    void fadeDisabledMeansAlwaysOpaque() {
        assertEquals(HudTracking.OPAQUE, HudTracking.fadeOpacityByte(0, 0));
    }

    @Test
    void fadeOpacityByteMapsLinearly() {
        assertEquals(HudTracking.OPAQUE, HudTracking.fadeOpacityByte(1600, 600));
        assertEquals(Math.round(255 * 0.5f), HudTracking.fadeOpacityByte(300, 600));
        assertEquals(0, HudTracking.fadeOpacityByte(1, 600));
    }
}
