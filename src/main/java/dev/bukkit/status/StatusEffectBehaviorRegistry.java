package dev.bukkit.status;

import java.util.EnumMap;
import java.util.Map;

import dev.core.status.StatusEffectType;

/**
 * Maps each {@link StatusEffectType} to the Bukkit behavior that applies its
 * crowd control to the vanilla entity. The single extension point for how a
 * CC type plays out (see {@code DMain.onEnable}). Types without a registered
 * behavior (e.g. CC immune, which is purely a blocking rule in the core
 * manager) simply get no vanilla manipulation.
 */
public final class StatusEffectBehaviorRegistry {

	private static final Map<StatusEffectType, StatusEffectBehavior> BEHAVIORS = new EnumMap<>(StatusEffectType.class);

	private StatusEffectBehaviorRegistry() {
	}

	public static void register(StatusEffectType type, StatusEffectBehavior behavior) {
		if (type != null && behavior != null) {
			BEHAVIORS.put(type, behavior);
		}
	}

	public static StatusEffectBehavior get(StatusEffectType type) {
		return type == null ? null : BEHAVIORS.get(type);
	}
}