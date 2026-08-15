package dev.core.item.equipment;

import dev.core.item.RPGItem;

public enum EquipmentSlot {

	HEAD, CHEST, LEGS, FEET, MAIN_HAND, OFF_HAND;

	/**
	 * Whether an {@link RPGItem} may be equipped in this slot. An item only
	 * grants its active stats when it is inside its designated slot; holding an
	 * armor piece in the hand (or vice versa) must NOT apply its active stats.
	 * The one exception: weapons (designated {@code MAIN_HAND}) may also be
	 * equipped in the {@code OFF_HAND} (dual wielding).
	 */
	public boolean canEquip(RPGItem item) {
		EquipmentSlot designated = item.getEquipmentSlot();
		return designated == this || (this == OFF_HAND && designated == MAIN_HAND);
	}

}
