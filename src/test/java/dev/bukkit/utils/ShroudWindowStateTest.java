package dev.bukkit.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * State matrix for the shroud window in {@link StealthRegistry}: inside vs
 * outside the radius, cross-world safety, expiry, and the 1.5s reveal window
 * that opens on attacking/leaving. Uses the package-visible clock hook to
 * time-travel instead of sleeping.
 */
class ShroudWindowStateTest {

    private static final double RADIUS = 7.0;
    private static final long DURATION_MS = 6000L;
    private static final long REVEAL_MS = 1500L;

    private final AtomicLong now = new AtomicLong(100_000L);
    private World world;
    private UUID uuid;
    private Player player;

    @BeforeEach
    void setUp() {
        StealthRegistry.CLOCK = now::get;
        world = mock(World.class);
        uuid = UUID.randomUUID();
        player = playerAt(0, 0);
        StealthRegistry.clearAll(uuid);
    }

    @AfterEach
    void tearDown() {
        StealthRegistry.clearAll(uuid);
        StealthRegistry.CLOCK = System::currentTimeMillis;
    }

    private Player playerAt(double x, double z) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.getLocation()).thenReturn(new Location(world, x, 64, z));
        return p;
    }

    private void placeShroudAroundOrigin() {
        StealthRegistry.placeShroud(uuid, new Location(world, 0, 64, 0), RADIUS, DURATION_MS);
    }

    @Test
    void standingInsideRadiusIsHidden() {
        placeShroudAroundOrigin();
        Player inside = playerAt(3, 3); // ~4.24 blocks from center

        assertTrue(StealthRegistry.isShrouded(inside));
        assertTrue(StealthRegistry.isShroudedDeterministic(inside));
        assertTrue(StealthRegistry.shouldHideFromMob(inside));
    }

    @Test
    void outsideRadiusIsExposed() {
        placeShroudAroundOrigin();
        Player outside = playerAt(8, 0);

        assertFalse(StealthRegistry.isShrouded(outside));
        assertFalse(StealthRegistry.isShroudedDeterministic(outside));
        // no orb equipped anywhere: hide check must be a deterministic false too
        assertFalse(StealthRegistry.shouldHideFromMob(outside));
    }

    @Test
    void crossWorldPlayerIsExposedWithoutThrowing() {
        placeShroudAroundOrigin();
        World otherWorld = mock(World.class);
        Player elsewhere = playerAt(0, 0);
        when(elsewhere.getLocation()).thenReturn(new Location(otherWorld, 0, 64, 0));

        assertFalse(StealthRegistry.isShrouded(elsewhere));
        assertFalse(StealthRegistry.shouldHideFromMob(elsewhere));
    }

    @Test
    void shroudExpiresAndStaysGone() {
        placeShroudAroundOrigin();

        now.addAndGet(DURATION_MS + 1);

        assertFalse(StealthRegistry.hasShroud(uuid));
        assertEquals(0L, StealthRegistry.getRemainingMs(uuid));
        assertFalse(StealthRegistry.isShrouded(player));
    }

    @Test
    void revealWindowOpensThenClosesBackToShrouded() {
        placeShroudAroundOrigin();

        StealthRegistry.reveal(uuid, REVEAL_MS);
        assertTrue(StealthRegistry.isRevealed(uuid));
        assertFalse(StealthRegistry.isShrouded(player), "revealed player must be targetable");
        assertFalse(StealthRegistry.shouldHideFromMob(player));

        now.addAndGet(REVEAL_MS + 1);

        assertFalse(StealthRegistry.isRevealed(uuid));
        assertTrue(StealthRegistry.isShrouded(player), "shroud protection resumes after reveal ends");
    }

    @Test
    void removingShroudClearsAllState() {
        placeShroudAroundOrigin();
        StealthRegistry.reveal(uuid, REVEAL_MS);

        StealthRegistry.removeShroud(uuid);

        assertFalse(StealthRegistry.hasShroud(uuid));
        assertFalse(StealthRegistry.isRevealed(uuid));
        assertFalse(StealthRegistry.isShrouded(player));
    }

    @Test
    void deterministicQueryNeverRollsWithoutShroud() {
        StealthRegistry.setPassiveEquipped(uuid, true); // 20% dodge holder

        for (int i = 0; i < 50; i++) {
            assertFalse(StealthRegistry.isShroudedDeterministic(player),
                    "deterministic query must never report stealth without an active shroud");
        }
    }

    @Test
    void dodgeVerdictIsStableWithinOneWindow() {
        StealthRegistry.setPassiveEquipped(uuid, true);

        boolean firstVerdict = StealthRegistry.shouldHideFromMob(player);
        for (int i = 0; i < 30; i++) {
            assertEquals(firstVerdict, StealthRegistry.shouldHideFromMob(player),
                    "verdict flipped within the TTL window — consumers would disagree mid-acquisition");
        }
    }

    @Test
    void dodgeVerdictVariesAcrossWindows() {
        StealthRegistry.setPassiveEquipped(uuid, true);

        Set<Boolean> verdicts = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            verdicts.add(StealthRegistry.shouldHideFromMob(player));
            now.addAndGet(StealthRegistry.ROLL_TTL_MS + 1); // force a fresh roll each window
        }

        assertNotEquals(java.util.Collections.singleton(Boolean.FALSE), verdicts,
                "never dodged in 400 windows at 20% (p ≈ 1e-39) — cache never re-rolls");
    }

    @Test
    void unequipInvalidatesCachedVerdictImmediately() {
        StealthRegistry.setPassiveEquipped(uuid, true);
        StealthRegistry.shouldHideFromMob(player); // seed the cache

        StealthRegistry.setPassiveEquipped(uuid, false);

        assertFalse(StealthRegistry.shouldHideFromMob(player),
                "stale cached dodge must not outlive the passive");
    }
}
