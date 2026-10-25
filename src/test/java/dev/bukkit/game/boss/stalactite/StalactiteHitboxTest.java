package dev.bukkit.game.boss.stalactite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class StalactiteHitboxTest {

    private static final double MARGIN = StalactiteConfig.BOSS_COLLISION_MARGIN;

    @Test
    void bossBodyInsideFrustumOverlaps() {
        Location base = new Location(null, 0, 0, 0);
        BoundingBox boss = new BoundingBox(-0.5, -7, -0.5, 0.5, -3, 0.5);

        assertTrue(StalactiteHitbox.intersectsTaperedStalactite(boss, base, MARGIN));
    }

    @Test
    void bossBoxAboveBaseDoesNotOverlap() {
        Location base = new Location(null, 0, 0, 0);
        BoundingBox above = new BoundingBox(-1, 1, -1, 1, 3, 1);

        assertFalse(StalactiteHitbox.intersectsTaperedStalactite(above, base, MARGIN));
    }

    @Test
    void bossBoxBesideNarrowTipDoesNotOverlap() {
        Location base = new Location(null, 0, 0, 0);
        BoundingBox besideTip = new BoundingBox(3.5, -9.9, -0.5, 4.5, -8.0, 0.5);

        assertFalse(StalactiteHitbox.intersectsTaperedStalactite(besideTip, base, MARGIN));
    }

    @Test
    void bossBoxCanOverlapWideBaseFromTheSide() {
        Location base = new Location(null, 0, 0, 0);
        BoundingBox nearBase = new BoundingBox(2.0, -2.0, -0.5, 3.0, -1.0, 0.5);

        assertTrue(StalactiteHitbox.intersectsTaperedStalactite(nearBase, base, MARGIN));
    }
}
