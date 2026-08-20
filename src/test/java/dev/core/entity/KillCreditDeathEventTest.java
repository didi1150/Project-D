package dev.core.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.event.BukkitEventBus;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.ResourceStat;

/**
 * Kill credit: the last entity to land damage is carried into
 * {@link RPGEntityDeathEvent} so soul drops / rewards can credit the killer
 * (and, for summons, the summon's owner).
 */
class KillCreditDeathEventTest {

    @AfterEach
    void clearEntityManager() {
        EntityManager.getInstance().clear();
    }

    @Test
    void lethalHitCarriesTheAttackerAsKiller() {
        HitCountingEntity target = new HitCountingEntity(statsManager(), EntityType.MOB);
        RPGEntity attacker = new HitCountingEntity(statsManager(), EntityType.PLAYER);

        RPGEntity[] recorded = new RPGEntity[1];
        BukkitEventBus.getInstance().subscribe(new EventAction<>(event -> recorded[0] = event.getKiller(),
                RPGEntityDeathEvent.class));

        target.dealRPGDamage(attacker, target, 150, DamageType.PHYSICAL);

        assertFalse(target.isAlive(), "lethal hit must kill the target");
        assertSame(attacker, recorded[0], "death event must carry the killer");
        assertSame(attacker, target.getLastAttacker());
    }

    @Test
    void nonLethalHitUpdatesCreditWithoutKilling() {
        HitCountingEntity target = new HitCountingEntity(statsManager(), EntityType.MOB);
        RPGEntity attacker = new HitCountingEntity(statsManager(), EntityType.PLAYER);
        RPGEntity second = new HitCountingEntity(statsManager(), EntityType.PLAYER);

        target.dealRPGDamage(attacker, target, 10, DamageType.PHYSICAL);
        assertEquals(attacker, target.getLastAttacker(), "first landed hit must take credit");

        target.dealRPGDamage(second, target, 10, DamageType.PHYSICAL);
        assertEquals(second, target.getLastAttacker(), "latest landed hit must re-credit");
    }

    @Test
    void environmentDamageNeverTakesCredit() {
        HitCountingEntity target = new HitCountingEntity(statsManager(), EntityType.MOB);
        RPGEntity attacker = new HitCountingEntity(statsManager(), EntityType.PLAYER);

        target.dealRPGDamage(attacker, target, 10, DamageType.PHYSICAL);
        target.dealRPGDamage(null, target, 10, DamageType.PHYSICAL);

        assertEquals(attacker, target.getLastAttacker(), "null attacker must not steal credit");
    }

    @Test
    void deathWithoutPriorCreditHasNullKiller() {
        HitCountingEntity target = new HitCountingEntity(statsManager());

        RPGEntity[] recorded = new RPGEntity[1];
        EventAction<RPGEntityDeathEvent> action = new EventAction<>(event -> recorded[0] = event.getKiller(),
                RPGEntityDeathEvent.class);
        BukkitEventBus.getInstance().subscribe(action);
        target.onDeath();
        BukkitEventBus.getInstance().unsubscribe(action.getId());

        assertNull(recorded[0], "environment death must carry no killer");
    }

    // ---------------------------------------------------------------- stubs

    private static StatManager statsManager() {
        Map<StatType, Stat> stats = new HashMap<>();
        long now = System.currentTimeMillis();
        stats.put(StatType.HEALTH_RESOURCE, new ResourceStat("health", t -> 100.0, t -> 0.0, now));
        return new StatManager(stats);
    }

    private static final class HitCountingEntity extends RPGEntity {
        HitCountingEntity(StatManager statManager) {
            this(statManager, EntityType.MOB);
        }

        HitCountingEntity(StatManager statManager, EntityType type) {
            super(statManager, UUID.randomUUID(), "target", type, new NoopEffectManager(),
                    BukkitEventBus.getInstance(), RPGClassType.NONE);
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