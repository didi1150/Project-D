package dev.core.stat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.loader.StatLoader;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;
import dev.core.stat.provider.adapter.ItemStatProvider;
import dev.core.ability.Ability;
import dev.core.ability.Effect;

/**
 * Regression: class selection reinstalls a fresh stat set into the StatManager
 * (PlayerProgression.setActiveClass → clearAll + addAll). Those fresh
 * ResourceStats must be wired to the StatEngine, otherwise their cap stays at
 * the raw base value and mana/health regen stops below the boosted max.
 */
class StatManagerResourceWiringTest {

    @Test
    void freshResourceStatsInstalledAfterConstructionFollowEngineMax() {
        TestRPGEntity entity = new TestRPGEntity(baseStats());

        RPGItem sceptre = RPGItem.builder("TEST_SCEPTRE", "Test Sceptre", EquipmentSlot.MAIN_HAND)
                .withActiveStats(List.of(
                        StatModifier.builder(150.0, StatModifierType.FLAT, StatType.MANA_MAX, "TEST_SCEPTRE").build(),
                        StatModifier.builder(1000.0, StatModifierType.FLAT, StatType.MANA_REGEN, "TEST_SCEPTRE")
                                .build()))
                .build();
        entity.getStatEngine().registerProvider(new ItemStatProvider(sceptre, true));

        // Same sequence as PlayerProgression.setActiveClass: wipe and reinstall a
        // freshly merged stat set, producing brand new ResourceStat instances.
        entity.getStatManager().addAll(StatLoader.mergeStats(baseStats(), Map.of()));

        long now = System.currentTimeMillis();
        // Boosted max visible through the engine...
        assertEquals(250.0, entity.getStatEngineAdapter().getCurrentValue(StatType.MANA_MAX, now), 0.001);
        // ...and the resource pool must cap/regen against that same boosted max.
        assertEquals(250.0, entity.getStatManager().getMaxValue(StatType.MANA_RESOURCE, now), 0.001);

        entity.getStatManager().setCurrentValue(StatType.MANA_RESOURCE, 0);
        // Item gives +1000 mana regen per 5s -> ~200/s; two one-second ticks must
        // fill the pool all the way to the engine-boosted max (not stop at 100).
        entity.tick(now + 1000);
        entity.tick(now + 2000);
        assertEquals(250.0, entity.getStatManager().getCurrentValue(StatType.MANA_RESOURCE, now + 2000), 0.5);
    }

    private static Map<StatType, Stat> baseStats() {
        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100));
        stats.put(StatType.HEALTH_REGEN, new CombatStat("HEALTH_REGEN", 2));
        stats.put(StatType.HEAL_AND_SHIELD_POWER, new CombatStat("HEAL_AND_SHIELD_POWER", 0));
        stats.put(StatType.MANA_MAX, new CombatStat("MANA_MAX", 100));
        stats.put(StatType.MANA_REGEN, new CombatStat("MANA_REGEN", 4));
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
