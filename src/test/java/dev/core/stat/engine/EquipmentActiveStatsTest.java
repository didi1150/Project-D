package dev.core.stat.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentManager;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;

/**
 * Verifies that active stats from every equipment slot (HEAD, CHEST, LEGS,
 * FEET, MAIN_HAND, OFF_HAND) are applied on equip and removed on unequip via
 * the StatEngine adapter, and that the same item id equipped in two slots
 * behaves independently (provider ids are slot-qualified).
 */
class EquipmentActiveStatsTest {

    @Test
    void allSixSlotsApplyTheirActiveStatsOnEquip() {
        TestRPGEntity entity = new TestRPGEntity();
        EquipmentManager manager = entity.getEquipmentManager();
        long now = System.currentTimeMillis();

        manager.equipItem(EquipmentSlot.HEAD, item("HEAD_PIECE", EquipmentSlot.HEAD, StatType.ARMOR, 5));
        manager.equipItem(EquipmentSlot.CHEST, item("CHEST_PIECE", EquipmentSlot.CHEST, StatType.MAGIC_RESIST, 7));
        manager.equipItem(EquipmentSlot.LEGS, item("LEGS_PIECE", EquipmentSlot.LEGS, StatType.MOVE_SPEED, 9));
        manager.equipItem(EquipmentSlot.FEET, item("FEET_PIECE", EquipmentSlot.FEET, StatType.LETHALITY, 11));
        manager.equipItem(EquipmentSlot.MAIN_HAND, item("MAIN_WEAPON", EquipmentSlot.MAIN_HAND,
                StatType.ATTACK_DAMAGE, 20));
        manager.equipItem(EquipmentSlot.OFF_HAND, item("OFFHAND_TOME", EquipmentSlot.OFF_HAND,
                StatType.ABILITY_POWER, 15));

        assertEquals(base(entity, StatType.ARMOR, now) + 5,
                entity.getStatEngineAdapter().getCurrentValue(StatType.ARMOR, now), 0.001);
        assertEquals(base(entity, StatType.MAGIC_RESIST, now) + 7,
                entity.getStatEngineAdapter().getCurrentValue(StatType.MAGIC_RESIST, now), 0.001);
        assertEquals(base(entity, StatType.MOVE_SPEED, now) + 9,
                entity.getStatEngineAdapter().getCurrentValue(StatType.MOVE_SPEED, now), 0.001);
        assertEquals(base(entity, StatType.LETHALITY, now) + 11,
                entity.getStatEngineAdapter().getCurrentValue(StatType.LETHALITY, now), 0.001);
        assertEquals(base(entity, StatType.ATTACK_DAMAGE, now) + 20,
                entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
        assertEquals(base(entity, StatType.ABILITY_POWER, now) + 15,
                entity.getStatEngineAdapter().getCurrentValue(StatType.ABILITY_POWER, now), 0.001);
    }

    @Test
    void unequippingEachSlotRemovesOnlyThatSlotsBonus() {
        TestRPGEntity entity = new TestRPGEntity();
        EquipmentManager manager = entity.getEquipmentManager();
        long now = System.currentTimeMillis();

        manager.equipItem(EquipmentSlot.HEAD, item("HEAD_PIECE", EquipmentSlot.HEAD, StatType.ARMOR, 5));
        manager.equipItem(EquipmentSlot.MAIN_HAND, item("MAIN_WEAPON", EquipmentSlot.MAIN_HAND,
                StatType.ATTACK_DAMAGE, 20));
        manager.equipItem(EquipmentSlot.OFF_HAND, item("OFFHAND_TOME", EquipmentSlot.OFF_HAND,
                StatType.ABILITY_POWER, 15));

        manager.unequipItem(EquipmentSlot.MAIN_HAND);
        assertEquals(base(entity, StatType.ATTACK_DAMAGE, now),
                entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001,
                "weapon bonus must be removed on unequip");
        assertEquals(base(entity, StatType.ARMOR, now) + 5,
                entity.getStatEngineAdapter().getCurrentValue(StatType.ARMOR, now), 0.001,
                "other slots' bonuses must survive");
        assertEquals(base(entity, StatType.ABILITY_POWER, now) + 15,
                entity.getStatEngineAdapter().getCurrentValue(StatType.ABILITY_POWER, now), 0.001);

        manager.unequipItem(EquipmentSlot.HEAD);
        manager.unequipItem(EquipmentSlot.OFF_HAND);
        assertEquals(base(entity, StatType.ARMOR, now),
                entity.getStatEngineAdapter().getCurrentValue(StatType.ARMOR, now), 0.001);
        assertEquals(base(entity, StatType.ABILITY_POWER, now),
                entity.getStatEngineAdapter().getCurrentValue(StatType.ABILITY_POWER, now), 0.001);
        // Only the entity's permanent ModifierBucketProvider bridge remains.
        assertEquals(1, entity.getStatEngine().providerCount(), "all item providers unregistered after full unequip");
    }

    @Test
    void sameItemIdInTwoSlotsAppliesIndependently() {
        TestRPGEntity entity = new TestRPGEntity();
        EquipmentManager manager = entity.getEquipmentManager();
        long now = System.currentTimeMillis();

        RPGItem sword = item("DUAL_SWORD", EquipmentSlot.MAIN_HAND, StatType.ATTACK_DAMAGE, 20);

        assertDoesNotThrow(() -> manager.equipItem(EquipmentSlot.MAIN_HAND, sword),
                "equipping an item id that is already equipped elsewhere must not throw");
        assertDoesNotThrow(() -> manager.equipItem(EquipmentSlot.OFF_HAND, sword));

        assertEquals(base(entity, StatType.ATTACK_DAMAGE, now) + 40,
                entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001,
                "both slots contribute their bonus");

        manager.unequipItem(EquipmentSlot.MAIN_HAND);
        assertEquals(base(entity, StatType.ATTACK_DAMAGE, now) + 20,
                entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001,
                "unequipping one slot must not remove the other slot's bonus");

        manager.unequipItem(EquipmentSlot.OFF_HAND);
        assertEquals(base(entity, StatType.ATTACK_DAMAGE, now),
                entity.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now), 0.001);
        // Only the entity's permanent ModifierBucketProvider bridge remains.
        assertEquals(1, entity.getStatEngine().providerCount(), "no item providers left after full unequip");
    }

    private static double base(TestRPGEntity entity, StatType type, long now) {
        return entity.getStatManager().getCurrentValue(type, now);
    }

    private static RPGItem item(String id, EquipmentSlot slot, StatType statType, double amount) {
        return RPGItem.builder(id, id, slot)
                .withActiveStats(List.of(
                        StatModifier.builder(amount, StatModifierType.FLAT, statType, id).build()))
                .build();
    }

    private static final class TestRPGEntity extends RPGEntity {
        TestRPGEntity() {
            super(new StatManager(DefaultStats.getDefaultStats()), UUID.randomUUID(), "test", EntityType.PLAYER,
                    new NoopEffectManager(), null, RPGClassType.NONE);
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