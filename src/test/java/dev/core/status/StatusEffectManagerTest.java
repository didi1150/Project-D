package dev.core.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;

/**
 * Pure-logic tests for the crowd-control bookkeeping: durations, expiry,
 * stacking rules, the hard-vs-soft interaction, and CC immunity. Fakes a
 * headless RPGEntity; the manager under test is the real core
 * {@link StatusEffectManager}.
 */
class StatusEffectManagerTest {

    private static final long DURATION = 10_000;

    private StatusEffectManager manager;
    private RPGEntity entity;

    @BeforeEach
    void setUp() {
        manager = new StatusEffectManager();
        entity = new TestRPGEntity(manager);
    }

    @AfterEach
    void tearDown() {
        manager.cancelAll();
    }

    @Test
    void applyAddsEffectAndHasReturnsTrue() {
        assertTrue(manager.apply(entity, StatusEffectType.SLOWED, DURATION));
        assertTrue(manager.has(entity, StatusEffectType.SLOWED));
        assertEquals(1, manager.getActive(entity).size());
        assertFalse(manager.has(entity, StatusEffectType.ROOTED));
    }

    @Test
    void effectVanishesAfterItsDuration() {
        manager.apply(entity, StatusEffectType.SLOWED, 1_000);
        manager.tick(System.currentTimeMillis() + 2_000);
        assertFalse(manager.has(entity, StatusEffectType.SLOWED));
        assertTrue(manager.getActive(entity).isEmpty());
    }

    @Test
    void infiniteEffectNeverExpires() {
        manager.apply(entity, StatusEffectType.SLOWED, -1);
        manager.tick(Long.MAX_VALUE);
        assertTrue(manager.has(entity, StatusEffectType.SLOWED));
    }

    @Test
    void sameTypeReapplyRefreshesDuration() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION);
        long firstEnd = manager.getActive(entity).get(0).getEndTime();

        assertTrue(manager.apply(entity, StatusEffectType.SLOWED, 4 * DURATION));
        long secondEnd = manager.getActive(entity).get(0).getEndTime();
        assertTrue(secondEnd > firstEnd, "a re-apply must extend the duration");
        assertEquals(1, manager.getActive(entity).size(), "identical CC must not stack");
    }

    @Test
    void softCcsCoexist() {
        assertTrue(manager.apply(entity, StatusEffectType.SLOWED, DURATION));
        assertTrue(manager.apply(entity, StatusEffectType.ROOTED, DURATION));
        assertEquals(2, manager.getActive(entity).size());
    }

    @Test
    void hardCcClearsAllSoftCc() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION);
        manager.apply(entity, StatusEffectType.ROOTED, DURATION);

        assertTrue(manager.apply(entity, StatusEffectType.STUNNED, DURATION));
        assertFalse(manager.has(entity, StatusEffectType.SLOWED), "hard CC must clear soft CC");
        assertFalse(manager.has(entity, StatusEffectType.ROOTED), "hard CC must clear soft CC");
        assertTrue(manager.has(entity, StatusEffectType.STUNNED));
        assertTrue(manager.hasHardCc(entity));
    }

    @Test
    void onlyOneHardCcAtATime() {
        manager.apply(entity, StatusEffectType.STUNNED, DURATION);
        assertFalse(manager.apply(entity, StatusEffectType.AIRBORNE, DURATION),
                "a second hard CC must be rejected while one is active");
        assertFalse(manager.has(entity, StatusEffectType.AIRBORNE));
        // Soft CC still lands while a hard CC dominates.
        assertTrue(manager.apply(entity, StatusEffectType.SLOWED, DURATION));
        assertTrue(manager.has(entity, StatusEffectType.SLOWED));
    }

    @Test
    void ccImmuneCleansesActiveCcAndBlocksNewOnes() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION);
        manager.apply(entity, StatusEffectType.AIRBORNE, DURATION);

        assertTrue(manager.apply(entity, StatusEffectType.CC_IMMUNE, DURATION));
        assertFalse(manager.has(entity, StatusEffectType.SLOWED), "immunity must cleanse soft CC");
        assertFalse(manager.has(entity, StatusEffectType.AIRBORNE), "immunity must cleanse hard CC");
        assertFalse(manager.hasHardCc(entity));
        assertTrue(manager.isCcImmune(entity));

        assertFalse(manager.apply(entity, StatusEffectType.STUNNED, DURATION), "new CC must be rejected");
        assertFalse(manager.apply(entity, StatusEffectType.SLOWED, DURATION), "even soft CC must be rejected");
        assertTrue(manager.has(entity, StatusEffectType.CC_IMMUNE));
    }

    @Test
    void ccImmuneExpiresAndCcCanLandAgain() {
        manager.apply(entity, StatusEffectType.CC_IMMUNE, 1_000);
        manager.tick(System.currentTimeMillis() + 2_000);
        assertFalse(manager.isCcImmune(entity));
        assertTrue(manager.apply(entity, StatusEffectType.STUNNED, DURATION));
    }

    @Test
    void ccImmuneReapplyRefreshes() {
        manager.apply(entity, StatusEffectType.CC_IMMUNE, DURATION);
        assertTrue(manager.apply(entity, StatusEffectType.CC_IMMUNE, 4 * DURATION));
        assertTrue(manager.isCcImmune(entity));
    }

    @Test
    void removeEndsOnlyTheRequestedType() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION);
        manager.apply(entity, StatusEffectType.ROOTED, DURATION);

        assertTrue(manager.remove(entity, StatusEffectType.SLOWED));
        assertFalse(manager.remove(entity, StatusEffectType.SLOWED), "a second remove must report nothing removed");
        assertTrue(manager.has(entity, StatusEffectType.ROOTED));
        assertFalse(manager.has(entity, StatusEffectType.SLOWED));
    }

    @Test
    void removeAllEndsEverything() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION);
        manager.apply(entity, StatusEffectType.ROOTED, DURATION);
        manager.removeAll(entity);
        assertTrue(manager.getActive(entity).isEmpty());
    }

    @Test
    void cancelAllEndsEverything() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION);
        assertTrue(manager.apply(entity, StatusEffectType.STUNNED, DURATION));
        manager.cancelAll();
        assertTrue(manager.getActive(entity).isEmpty());
    }

    @Test
    void expiredEffectsAreRemovedOnTick() {
        manager.apply(entity, StatusEffectType.SLOWED, 1_000);
        manager.apply(entity, StatusEffectType.SLOWED, 1_000);
        manager.tick(System.currentTimeMillis() + 2_000);
        assertTrue(manager.getActive(entity).isEmpty());
    }

    @Test
    void defaultFadeFollowsTheTypeAndExplicitFlagWins() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION); // SLOWED defaults to fade-out
        assertTrue(manager.getActive(entity).get(0).isFadeOut());

        manager.remove(entity, StatusEffectType.SLOWED);
        manager.apply(entity, StatusEffectType.SLOWED, DURATION, false, 1.0); // explicit abrupt
        assertFalse(manager.getActive(entity).get(0).isFadeOut());

        manager.remove(entity, StatusEffectType.SLOWED);
        manager.apply(entity, StatusEffectType.STUNNED, DURATION); // STUNNED defaults to abrupt
        assertFalse(manager.getActive(entity).get(0).isFadeOut());
    }

    @Test
    void entityApiDelegatesToItsManager() {
        TestRPGEntity wired = new TestRPGEntity(manager);
        assertTrue(wired.applyStatusEffect(StatusEffectType.STUNNED, DURATION));
        assertTrue(wired.hasStatusEffect(StatusEffectType.STUNNED));
        assertFalse(wired.canAttack(), "hard CC must block attacking");
        assertTrue(wired.applyStatusEffect(StatusEffectType.SLOWED, DURATION, true));
        assertTrue(wired.hasStatusEffect(StatusEffectType.SLOWED));
        assertFalse(wired.canAttack(), "still stunned");
    }

    @Test
    void applicationOrderIsPreservedForStacking() {
        manager.apply(entity, StatusEffectType.SLOWED, DURATION);
        manager.apply(entity, StatusEffectType.ROOTED, DURATION);
        List<ActiveStatusEffect> active = manager.getActive(entity);
        assertEquals(StatusEffectType.SLOWED, active.get(0).getType());
        assertEquals(StatusEffectType.ROOTED, active.get(1).getType());
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(StatusEffectManager statusEffects) {
            super(new StatManager(DefaultStats.getDefaultStats()), UUID.randomUUID(), "test", EntityType.MOB,
                    null, null, RPGClassType.NONE, statusEffects);
        }
    }
}