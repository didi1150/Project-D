package dev.core.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import dev.core.ability.Ability;
import dev.core.ability.Effect;

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
        RPGEntity attacker = new HitCountingEntity(statsManager(), EntityType.PLAYER);

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
        // Cross-team attacker: PLAYER is on the player team, the mob target is
        // not, so the allied-team guard lets the hit through.
        RPGEntity attacker = new HitCountingEntity(statsManager(), EntityType.PLAYER);

        double healthBefore = target.getHealth();

        RPGDamageResult result = target.dealRPGDamage(attacker, target, 50, DamageType.PHYSICAL);

        assertEquals(DamageResult.NORMAL, result.getResult());
        assertEquals(1, target.hitReactions, "a landed hit must trigger the central hit-reaction hook");
        assertEquals(healthBefore - result.getDamage(), target.getHealth(), 0.001);
    }

    @Test
    void deadTargetTakesNoDamageNoReactionNoIndicatorData() {
        HitCountingEntity target = new HitCountingEntity(statsManager());
        RPGEntity attacker = new HitCountingEntity(statsManager(), EntityType.PLAYER);

        target.onDeath();
        double healthAfterDeath = target.getHealth();

        RPGDamageResult result = target.dealRPGDamage(attacker, target, 50, DamageType.PHYSICAL);

        assertEquals(DamageResult.DENY, result.getResult(), "dead (ghost) targets must be denied damage");
        assertEquals(0.0, result.getDamage(), 0.001);
        assertEquals(healthAfterDeath, target.getHealth(), 0.001, "dead target health must not change");
        assertEquals(0, target.hitReactions, "dead target must not get a hurt reaction");
    }

    @Test
    void reviveMarksEntityAliveAndDamageableAgain() {
        EntityManager manager = EntityManager.getInstance();
        HitCountingEntity target = new HitCountingEntity(statsManager());
        RPGEntity attacker = new HitCountingEntity(statsManager(), EntityType.PLAYER);
        manager.registerEntity(target);

        target.onDeath();
        assertFalse(target.isAlive(), "dead entity must not be alive");
        assertTrue(manager.isDead(target.getUuid()));

        manager.revive(target.getUuid());

        assertTrue(target.isAlive(), "revive must mark the entity alive again");
        assertFalse(manager.isDead(target.getUuid()));
        assertTrue(manager.getAliveEntities().contains(target),
                "revived entity must be back in the alive list");

        RPGDamageResult result = target.dealRPGDamage(attacker, target, 50, DamageType.PHYSICAL);

        assertEquals(DamageResult.NORMAL, result.getResult(), "revived entity must take damage again");
        assertEquals(1, target.hitReactions, "revived entity must get hurt reactions again");
    }

    @AfterEach
    void clearEntityManager() {
        EntityManager.getInstance().clear();
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
            this(statManager, EntityType.MOB);
        }

        HitCountingEntity(StatManager statManager, EntityType type) {
            super(statManager, UUID.randomUUID(), "target", type, new NoopEffectManager(),
                    BukkitEventBus.getInstance(), RPGClassType.NONE);
        }

        @Override
        protected void playHitReaction(RPGEntity attacker) {
            hitReactions++;
        }
    }

    private static final class NoopEffectManager implements EffectManagerInterface {
        @Override
        public Effect cast(RPGEntity entity, Ability ability) {
            return null;
        }

        @Override
        public boolean canActivate(RPGEntity entity, Ability ability) {
            return false;
        }

        @Override
        public long remainingCooldown(RPGEntity entity, Ability ability) {
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
