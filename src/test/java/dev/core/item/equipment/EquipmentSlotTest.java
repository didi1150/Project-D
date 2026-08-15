package dev.core.item.equipment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.core.item.RPGItem;

/**
 * Active stats must only apply when an item sits in its designated equipment
 * slot: holding an armor piece in the hand (or a weapon in the helmet slot)
 * grants nothing. The only exception is dual wielding: MAIN_HAND weapons may
 * also be equipped in the OFF_HAND.
 */
class EquipmentSlotTest {

    private static RPGItem item(EquipmentSlot slot) {
        return RPGItem.builder("TEST_" + slot.name(), "Test " + slot.name(), slot).build();
    }

    @Test
    void weaponFitsMainHandAndOffHandButNotArmorSlots() {
        RPGItem weapon = item(EquipmentSlot.MAIN_HAND);
        assertTrue(EquipmentSlot.MAIN_HAND.canEquip(weapon));
        assertTrue(EquipmentSlot.OFF_HAND.canEquip(weapon), "weapons may be dual-wielded in the off hand");
        assertFalse(EquipmentSlot.HEAD.canEquip(weapon));
        assertFalse(EquipmentSlot.CHEST.canEquip(weapon));
        assertFalse(EquipmentSlot.LEGS.canEquip(weapon));
        assertFalse(EquipmentSlot.FEET.canEquip(weapon));
    }

    @Test
    void armorOnlyFitsItsOwnSlot() {
        RPGItem helmet = item(EquipmentSlot.HEAD);
        assertTrue(EquipmentSlot.HEAD.canEquip(helmet));
        assertFalse(EquipmentSlot.MAIN_HAND.canEquip(helmet), "armor held in the hand must not apply active stats");
        assertFalse(EquipmentSlot.OFF_HAND.canEquip(helmet));
        assertFalse(EquipmentSlot.CHEST.canEquip(helmet));
        assertFalse(EquipmentSlot.FEET.canEquip(helmet));
    }

    @Test
    void offHandItemOnlyFitsOffHand() {
        RPGItem offHand = item(EquipmentSlot.OFF_HAND);
        assertTrue(EquipmentSlot.OFF_HAND.canEquip(offHand));
        assertFalse(EquipmentSlot.MAIN_HAND.canEquip(offHand));
        assertFalse(EquipmentSlot.FEET.canEquip(offHand));
    }

    @Test
    void chestLegsFeetItemsOnlyFitTheirOwnSlots() {
        RPGItem chestplate = item(EquipmentSlot.CHEST);
        RPGItem leggings = item(EquipmentSlot.LEGS);
        RPGItem boots = item(EquipmentSlot.FEET);
        assertTrue(EquipmentSlot.CHEST.canEquip(chestplate));
        assertFalse(EquipmentSlot.HEAD.canEquip(chestplate));
        assertTrue(EquipmentSlot.LEGS.canEquip(leggings));
        assertFalse(EquipmentSlot.FEET.canEquip(leggings));
        assertTrue(EquipmentSlot.FEET.canEquip(boots));
        assertFalse(EquipmentSlot.CHEST.canEquip(boots));
    }
}