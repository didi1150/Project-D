package dev.core.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaseRollMathTest {

    @Test
    void progressStartsAtZeroAndEndsAtOne() {
        assertEquals(0.0, CaseRollMath.progress(0.0), 0.000001);
        assertEquals(1.0, CaseRollMath.progress(1.0), 0.000001);
    }

    @Test
    void progressIsMonotonic() {
        double previous = 0.0;
        for (int i = 0; i <= 1000; i++) {
            double value = CaseRollMath.progress(i / 1000.0);
            assertTrue(value >= previous, "progress must not move backwards at i=" + i);
            previous = value;
        }
    }

    @Test
    void speedAcceleratesAtTheStartAndDeceleratesAtTheEnd() {
        double initial = CaseRollMath.speedAt(0.0);
        double early = CaseRollMath.speedAt(0.05);
        double cruise = CaseRollMath.speedAt(0.4); // Peak speed sustained longer
        double late = CaseRollMath.speedAt(0.85);  // Deceleration happens later
        double veryLate = CaseRollMath.speedAt(0.99);

        assertTrue(early > initial, "speed should instantly rocket up right after the start");
        assertTrue(cruise >= early, "speed should maintain a high cruise phase");
        assertTrue(late < cruise, "speed should decelerate near the end");
        assertTrue(veryLate > 0, "the reel should still crawl at the end");
        assertTrue(veryLate < late, "the final crawl should keep slowing down");
    }

    @Test
    void decelerationTailStillMovesDuringTheLastPortionOfTheRoll() {
        // Since high speed is held longer, progress at 70% will be higher, 
        // but the final 20-30% still handles the braking crawl.
        assertTrue(CaseRollMath.progress(0.7) < 0.99,
                "the curve should still have visible travel left after 70% of the duration");
        assertTrue(CaseRollMath.progress(0.85) < 0.999,
                "the final crawl should not be effectively finished at 85% of the duration");
    }

    @Test
    void offsetLandsExactlyOnTheFinalIndex() {
        int maxOffset = 53;
        assertEquals(0, CaseRollMath.offsetAtTick(0, 60, maxOffset));
        assertEquals(maxOffset, CaseRollMath.offsetAtTick(60, 60, maxOffset));

        int previous = -1;
        for (int tick = 0; tick <= 60; tick++) {
            int offset = CaseRollMath.offsetAtTick(tick, 60, maxOffset);
            assertTrue(offset >= previous, "offset must advance monotonically");
            assertTrue(offset <= maxOffset);
            previous = offset;
        }
    }

    @Test
    void finalOffsetIsOnlyReachedOnTheFinalTick() {
        int totalTicks = 100;
        int maxOffset = 27;
        for (int tick = 0; tick < totalTicks; tick++) {
            assertTrue(CaseRollMath.offsetAtTick(tick, totalTicks, maxOffset) < maxOffset,
                    "target offset must not appear before the roll ends at tick " + tick);
        }
        assertEquals(maxOffset, CaseRollMath.offsetAtTick(totalTicks, totalTicks, maxOffset));
    }

    @Test
    void continuousPositionCanDriveSubSlotRenderers() {
        int maxOffset = 53;
        assertEquals(0.0, CaseRollMath.positionAtTick(0, 60, maxOffset), 0.000001);
        assertEquals(maxOffset, CaseRollMath.positionAtTick(60, 60, maxOffset), 0.000001);

        double previous = -1.0;
        for (int tick = 0; tick <= 60; tick++) {
            double position = CaseRollMath.positionAtTick(tick, 60, maxOffset);
            assertTrue(position >= previous, "continuous position must advance monotonically");
            assertTrue(position <= maxOffset);
            previous = position;
        }
    }

    @Test
    void topSpeedDoesNotChangeWithDuration() {
        double topSpeed = 12.0;
        int shortTicks = 80;
        int longTicks = 270;

        int shortOffset = CaseRollMath.maxOffsetForTopSpeed(shortTicks, topSpeed);
        int longOffset = CaseRollMath.maxOffsetForTopSpeed(longTicks, topSpeed);

        assertTrue(longOffset > shortOffset, "longer rolls should travel farther");

        double shortActualTopSpeed = CaseRollMath.peakSpeed() * shortOffset / (shortTicks / 20.0);
        double longActualTopSpeed = CaseRollMath.peakSpeed() * longOffset / (longTicks / 20.0);

        assertEquals(topSpeed, shortActualTopSpeed, 0.5);
        assertEquals(topSpeed, longActualTopSpeed, 0.5);
        assertEquals(shortActualTopSpeed, longActualTopSpeed, 0.5);
    }

    @Test
    void decelerationGrowsTowardTheEndOfTheRoll() {
        double mid = CaseRollMath.speedAt(0.6);
        double late = CaseRollMath.speedAt(0.75);
        double finalApproach = CaseRollMath.speedAt(0.9);

        assertTrue(late < mid, "speed should keep dropping later in the roll");
        assertTrue(finalApproach < late, "the final approach should be slower than the mid roll");
        assertTrue((late - finalApproach) / (finalApproach == 0 ? 1 : finalApproach) > 0.5,
                "the late deceleration should be pronounced");
    }
}