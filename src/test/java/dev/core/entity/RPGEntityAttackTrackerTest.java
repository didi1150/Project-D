package dev.core.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.status.NoopStatusEffectManager;

class RPGEntityAttackTrackerTest {

    @Test
    void zeroAttackSpeedDoesNotLockOutAttacks() {
        assertTrue(trackerWithAttackSpeed(0.0).canAttack(),
                "a missing/zero ATTACK_SPEED must fall back to no cooldown instead of an infinite one");
    }

    @Test
    void nonFiniteAttackSpeedDoesNotLockOutAttacks() {
        assertTrue(trackerWithAttackSpeed(Double.NaN).canAttack());
        assertTrue(trackerWithAttackSpeed(Double.POSITIVE_INFINITY).canAttack());
    }

    @Test
    void fractionalAttackSpeedEnforcesCooldownThenAllowsAttack() throws Exception {
        RPGEntityAttackTracker tracker = trackerWithAttackSpeed(0.5);

        assertFalse(tracker.canAttack(), "the first swing is still inside the 2s cooldown window");

        Field lastValidAttack = RPGEntityAttackTracker.class.getDeclaredField("lastValidAttack");
        lastValidAttack.setAccessible(true);
        lastValidAttack.setLong(tracker, System.currentTimeMillis() - 3_000L);

        assertTrue(tracker.canAttack(), "after the 2s cooldown the attack must be allowed");
    }

    private static RPGEntityAttackTracker trackerWithAttackSpeed(double attackSpeed) {
        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.ATTACK_SPEED, new CombatStat("ATTACK_SPEED", attackSpeed));
        RPGEntity entity = new RPGEntity(new StatManager(stats), UUID.randomUUID(), "test", EntityType.MOB,
                mock(EffectManagerInterface.class), mock(EventBusInterface.class), RPGClassType.NONE,
                NoopStatusEffectManager.getInstance()) {
        };
        return new RPGEntityAttackTracker(entity);
    }
}
