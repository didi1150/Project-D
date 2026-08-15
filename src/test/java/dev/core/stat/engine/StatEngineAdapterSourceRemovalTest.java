package dev.core.stat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;

/**
 * StatEngineAdapter exposes modifier removal by source id so callers can strip
 * every modifier contributed by a source (buff, ability, set) in one call.
 */
class StatEngineAdapterSourceRemovalTest {

    @Test
    void removesOnlyModifiersMatchingSourceId() {
        TestRPGEntity entity = new TestRPGEntity(baseStats());
        StatEngineAdapter adapter = entity.getStatEngineAdapter();
        long now = System.currentTimeMillis();

        adapter.addStatModifier(StatModifier.builder(10.0, StatModifierType.FLAT, StatType.ATTACK_DAMAGE, "buff:haste")
                .build());
        adapter.addStatModifier(StatModifier.builder(20.0, StatModifierType.FLAT, StatType.ATTACK_DAMAGE, "buff:rage")
                .build());
        adapter.addStatModifier(StatModifier.builder(5.0, StatModifierType.FLAT, StatType.ATTACK_SPEED, "buff:haste")
                .build());
        adapter.addStatModifier(StatModifier.builder(7.0, StatModifierType.FLAT, StatType.ATTACK_SPEED, "buff:rage")
                .build());

        // 10 base + 10 (haste) + 20 (rage)
        assertEquals(40.0, adapter.getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);

        adapter.removeModifiersBySource("buff:haste");

        // 10 base + 20 (rage); haste's effects removed from every stat
        assertEquals(30.0, adapter.getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
        // 5 base + 7 (rage); other sources untouched
        assertEquals(12.0, adapter.getCurrentValue(StatType.ATTACK_SPEED, now), 0.001);
    }

    @Test
    void removingUnknownSourceIsNoOp() {
        TestRPGEntity entity = new TestRPGEntity(baseStats());
        StatEngineAdapter adapter = entity.getStatEngineAdapter();
        long now = System.currentTimeMillis();

        adapter.addStatModifier(StatModifier.builder(10.0, StatModifierType.FLAT, StatType.ATTACK_DAMAGE, "buff:haste")
                .build());
        adapter.removeModifiersBySource("buff:nope");

        // 10 base + 10 (haste)
        assertEquals(20.0, adapter.getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
    }

    private static Map<StatType, Stat> baseStats() {
        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.ATTACK_DAMAGE, new CombatStat("ATTACK_DAMAGE", 10));
        stats.put(StatType.ATTACK_SPEED, new CombatStat("ATTACK_SPEED", 5));
        stats.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100));
        stats.put(StatType.MANA_MAX, new CombatStat("MANA_MAX", 100));
        return stats;
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity(Map<StatType, Stat> stats) {
            super(new StatManager(stats), UUID.randomUUID(), "test", EntityType.PLAYER, new NoopEffectManager(), null,
                    RPGClassType.NONE);
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