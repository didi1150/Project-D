package dev.bukkit.game.boss.pillar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PillarMathTest {

    @Test
    void aggregateAndWeakestFractionsClamp() {
        assertEquals(0.75, PillarMath.aggregateFraction(75, 100), 0.0001);
        assertEquals(0.0, PillarMath.aggregateFraction(10, 0), 0.0001);
        assertEquals(0.5, PillarMath.weakestFraction(new double[] { 1.0, 0.5, 0.8 }), 0.0001);
        assertEquals(0.0, PillarMath.weakestFraction(new double[] {}), 0.0001);
    }

    @Test
    void holdingRequiresWeakestPillarAboveThreshold() {
        assertTrue(PillarMath.isHolding(0.95, 0.95));
        assertFalse(PillarMath.isHolding(0.94, 0.95));
    }

    @Test
    void bossRisesWhenPillarsDropBelowThreshold() {
        assertEquals(0.0, PillarMath.heightIncrease(1.0, 0.95, 0.95, 0.1, 1.0, 1.0, 10), 0.0001);
        assertTrue(PillarMath.heightIncrease(0.94, 0.94, 0.95, 0.1, 1.0, 1.0, 10) > 0.0);
        assertEquals(0.1, PillarMath.heightIncrease(0.0, 0.0, 0.95, 0.1, 1.0, 1.0, 10), 0.0001);
        assertEquals(1.0, PillarMath.heightIncrease(0.0, 0.0, 0.95, 0.1, 1.0, 1.0, 0), 0.0001);
    }

    @Test
    void ascentGrowsMonotonicallyAsIntegrityFalls() {
        double healthy = PillarMath.heightIncrease(0.9, 0.9, 0.95, 0.1, 1.0, 1.0, 10);
        double damaged = PillarMath.heightIncrease(0.5, 0.5, 0.95, 0.1, 1.0, 1.0, 10);
        double broken = PillarMath.heightIncrease(0.0, 0.0, 0.95, 0.1, 1.0, 1.0, 10);
        assertTrue(healthy < damaged);
        assertTrue(damaged < broken);
    }

    @Test
    void chainSagInterpolatesFromTautToSlack() {
        assertEquals(0.02, PillarMath.chainSag(1.0, 0.02, 0.45), 0.0001);
        assertEquals(0.45, PillarMath.chainSag(0.0, 0.02, 0.45), 0.0001);
        assertEquals(0.235, PillarMath.chainSag(0.5, 0.02, 0.45), 0.0001);
    }
}
