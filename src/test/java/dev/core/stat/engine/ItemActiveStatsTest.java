package dev.core.stat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;
import dev.core.stat.provider.adapter.ItemStatProvider;
import dev.core.ability.Ability;
import dev.core.ability.Effect;

/**
 * Bug regression: an equipped weapon's active stats are registered into the
 * StatEngine (ItemStatProvider), but melee + bone damage used to read the legacy
 * StatManager, which never sees them (fist == weapon). The engine adapter is the
 * correct read source.
 */
class ItemActiveStatsTest {

    @Test
    void weaponActiveAttackDamageIsVisibleThroughEngineAdapter() {
        TestRPGEntity entity = new TestRPGEntity();
        RPGItem weapon = RPGItem.builder("TEST_SWORD", "Test Sword", EquipmentSlot.MAIN_HAND)
                .withActiveStats(List.of(StatModifier.builder(20.0, StatModifierType.FLAT, StatType.ATTACK_DAMAGE,
                        "TEST_SWORD").build()))
                .build();

        entity.getStatEngine().registerProvider(new ItemStatProvider(weapon, true));

        long now = System.currentTimeMillis();
        double base = entity.getStatManager().getCurrentValue(StatType.ATTACK_DAMAGE, now);
        // The legacy StatManager read (old buggy path) never sees the weapon bonus...
        assertEquals(base, entity.getStatManager().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
        // ...but the engine adapter used by the fixed melee/bone damage does.
        assertEquals(base + 20.0, entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
    }

    @Test
    void unregisteringItemRemovesItsAttackBonus() {
        TestRPGEntity entity = new TestRPGEntity();
        RPGItem weapon = RPGItem.builder("TEST_SWORD", "Test Sword", EquipmentSlot.MAIN_HAND)
                .withActiveStats(List.of(StatModifier.builder(20.0, StatModifierType.FLAT, StatType.ATTACK_DAMAGE,
                        "TEST_SWORD").build()))
                .build();
        ItemStatProvider provider = new ItemStatProvider(weapon, true);
        entity.getStatEngine().registerProvider(provider);

        long now = System.currentTimeMillis();
        double base = entity.getStatManager().getCurrentValue(StatType.ATTACK_DAMAGE, now);
        assertEquals(base + 20.0, entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);

        entity.getStatEngine().unregisterProvider(provider.getId());
        assertEquals(base, entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity() {
            super(new StatManager(DefaultStats.getDefaultStats()), UUID.randomUUID(), "test", EntityType.PLAYER,
                    new NoopEffectManager(), null, RPGClassType.NONE);
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
