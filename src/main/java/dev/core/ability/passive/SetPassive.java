package dev.core.ability.passive;

import dev.core.entity.RPGEntity;

/**
 * A config-resolved passive granted by an item set bonus (e.g. "heal aura",
 * "draws monster aggression"). Effects are expected to be implemented in the
 * Bukkit layer (listeners, entity ticks) and queried at runtime through
 * {@code EquipmentManager.hasSetPassive(id)}. The lifecycle hooks are provided
 * for passives that need per-holder state (e.g. a repeating task).
 */
public interface SetPassive {

	String getId();

	default void onApply(RPGEntity entity) {
	}

	default void onRemove(RPGEntity entity) {
	}
}
