package dev.bukkit.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Pure-math tests for the Smash shockwave: the cone (wedge) geometry and the
 * armor/magic-resist damage formula. Neither touches Bukkit, so they run
 * headless.
 */
class BukkitSmashConeTest {

    private static final double RANGE = BukkitSmashEffect.CONE_RANGE;
    private static final double COS_HALF = Math.cos(Math.toRadians(BukkitSmashEffect.CONE_HALF_ANGLE_DEG));

    // ---- Cone geometry --------------------------------------------------------

    @Test
    void straightAheadInsideCone() {
        assertTrue(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), new Vector(0, 0, -3), RANGE, COS_HALF));
    }

    @Test
    void straightAheadExactlyAtRangeIsInside() {
        assertTrue(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), new Vector(0, 0, -RANGE), RANGE, COS_HALF));
    }

    @Test
    void beyondRangeIsOutside() {
        assertFalse(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), new Vector(0, 0, -RANGE - 0.5), RANGE, COS_HALF));
    }

    @Test
    void exactlyOnTheEdgeIsInside() {
        // 18 degrees off the forward axis is the cone's boundary.
        double radians = Math.toRadians(BukkitSmashEffect.CONE_HALF_ANGLE_DEG);
        Vector offset = new Vector(Math.sin(radians) * 4, 0, -Math.cos(radians) * 4);
        assertTrue(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), offset, RANGE, COS_HALF));
    }

    @Test
    void justOutsideTheEdgeIsOutside() {
        double radians = Math.toRadians(BukkitSmashEffect.CONE_HALF_ANGLE_DEG + 2);
        Vector offset = new Vector(Math.sin(radians) * 4, 0, -Math.cos(radians) * 4);
        assertFalse(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), offset, RANGE, COS_HALF));
    }

    @Test
    void standingOnTheImpactIsInside() {
        assertTrue(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), new Vector(0, 0, 0), RANGE, COS_HALF));
    }

    @Test
    void directlyBehindIsOutside() {
        assertFalse(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), new Vector(0, 0, 2), RANGE, COS_HALF));
    }

    @Test
    void perpendicularIsOutside() {
        assertFalse(BukkitSmashEffect.inConeWedge(new Vector(1, 0, 0), new Vector(0, 0, 3), RANGE, COS_HALF));
    }

    @Test
    void verticalComponentIsIgnored() {
        // The wedge is horizontal: a flying high-up target's Y must not matter.
        assertTrue(BukkitSmashEffect.inConeWedge(new Vector(0, 0, -1), new Vector(0, 99, -3), RANGE, COS_HALF));
    }

    // ---- Damage formula -------------------------------------------------------

    @Test
    void baseDamageAppliesWithoutStats() {
        assertEquals(15, BukkitSmashEffect.smashDamage(0, 0, 1), 1e-9);
    }

    @Test
    void damageScalesWithArmorAndMagicResist() {
        double base = BukkitSmashEffect.smashDamage(0, 0, 1);
        double armored = BukkitSmashEffect.smashDamage(20, 0, 1);
        double resistant = BukkitSmashEffect.smashDamage(0, 20, 1);
        double both = BukkitSmashEffect.smashDamage(20, 20, 1);

        assertEquals(15, base, 1e-9);
        assertTrue(armored > base, "armor must raise the damage");
        assertTrue(resistant > base, "magic resist must raise the damage");
        assertEquals(armored + resistant - base, both, 1e-9, "armor and magic resist must add up");
    }

    @Test
    void damageScalesWithAbilityMultiplier() {
        double base = BukkitSmashEffect.smashDamage(10, 5, 1);
        assertEquals(base * 2, BukkitSmashEffect.smashDamage(10, 5, 2), 1e-9);
    }
}