package dev.core.entity.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SniperDamageTest {

    @Test
    void distanceMultiplierIsOneAtZeroDistance() {
        assertEquals(1.0, SniperDamage.distanceMultiplier(0, 0.05, 5.0), 0.000001);
    }

    @Test
    void distanceMultiplierGrowsExponentially() {
        double atTen = SniperDamage.distanceMultiplier(10, 0.05, 100.0);
        assertEquals(Math.exp(0.5), atTen, 0.000001);
        double atTwenty = SniperDamage.distanceMultiplier(20, 0.05, 100.0);
        assertEquals(Math.exp(1.0), atTwenty, 0.000001);
    }

    @Test
    void distanceMultiplierRespectsCap() {
        assertEquals(5.0, SniperDamage.distanceMultiplier(100, 0.05, 5.0), 0.000001);
    }

    @Test
    void damageAppliesBaseAndMultiplier() {
        assertEquals(20.0 * Math.exp(0.1), SniperDamage.damage(20.0, 2.0, 0.05, 10.0), 0.000001);
    }
}
