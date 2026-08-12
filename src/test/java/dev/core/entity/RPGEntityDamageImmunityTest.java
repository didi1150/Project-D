package dev.core.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.bukkit.event.BukkitEventBus;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.ResourceStat;

/**
 * The central {@link RPGEntity#dealRPGDamage} flow: an immune target takes no
 * damage and gets no hurt reaction; a landed hit lowers health AND triggers the
 * central {@code playHitReaction} hook (which Bukkit subclasses use to play the
 * vanilla flash/sound).
 */
class RPGEntityDamageImmunityTest {

    @Test
    void immuneTargetTakesNoDamageAndNoReaction() {
        HitCountingEntity target = new HitCountingEntity(statsManager());
        RPGEntity attacker = new HitCountingEntity(statsManager());

        double healthBefore = target.getHealth();
        target.setDamageImmune(true);

        RPGDamageResult result = target.dealRPGDamage(attacker, target, 50, DamageType.PHYSICAL);

        assertEquals(DamageResult.DENY, result.getResult());
        assertEquals(0.0, result.getDamage(), 0.001);
        assertEquals(healthBefore, target.getHealth(), 0.001, "immune target health must not change");
        assertEquals(0, target.hitReactions, "immune target must not get a hurt reaction");
    }

    @Test
    void landedHitLowersHealthAndTriggersCentralReaction() {
        HitCountingEntity target = new HitCountingEntity(statsManager());
        RPGEntity attacker = new HitCountingEntity(statsManager());

        double healthBefore = target.getHealth();

        RPGDamageResult result = target.dealRPGDamage(attacker, target, 50, DamageType.PHYSICAL);

        assertEquals(DamageResult.NORMAL, result.getResult());
        assertEquals(1, target.hitReactions, "a landed hit must trigger the central hit-reaction hook");
        assertEquals(healthBefore - result.getDamage(), target.getHealth(), 0.001);
    }

    // ---------------------------------------------------------------- stubs

    private static StatManager statsManager() {
        Map<StatType, Stat> stats = new HashMap<>();
        long now = System.currentTimeMillis();
        stats.put(StatType.HEALTH_RESOURCE, new ResourceStat("health", t -> 100.0, t -> 0.0, now));
        return new StatManager(stats);
    }

    private static final class HitCountingEntity extends RPGEntity {
        int hitReactions = 0;

        HitCountingEntity(StatManager statManager) {
            super(statManager, UUID.randomUUID(), "target", EntityType.MOB, new NoopEffectManager(),
                    BukkitEventBus.getInstance(), RPGClassType.NONE);
        }

        @Override
        protected void playHitReaction(RPGEntity attacker) {
            hitReactions++;
        }
    }

    private static final class NoopEffectManager implements EffectManagerInterface {
        @Override
        public dev.core.ability.Effect cast(RPGEntity entity, dev.core.ability.Ability ability) {
            return null;
        }

        @Override
        public boolean canActivate(RPGEntity entity, dev.core.ability.Ability ability) {
            return false;
        }

        @Override
        public long remainingCooldown(RPGEntity entity, dev.core.ability.Ability ability) {
            return 0;
        }

        @Override
        public void tick(long now) {
        }

        @Override
        public void cancelAll() {
        }
    }
}