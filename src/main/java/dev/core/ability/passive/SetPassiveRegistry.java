package dev.core.ability.passive;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of item set passives. Passives are resolved by id from the
 * {@code passives:} list of an item set bonus (see RPGItemLoader). Bukkit
 * implementations register themselves during plugin startup; anything the
 * loader cannot resolve is logged and skipped so a bad config id never breaks
 * item loading.
 */
public class SetPassiveRegistry {

	private static final Map<String, SetPassive> PASSIVES = new HashMap<>();

	public static void register(SetPassive passive) {
		if (passive == null || passive.getId() == null) {
			return;
		}
		PASSIVES.put(passive.getId(), passive);
	}

	public static Optional<SetPassive> get(String id) {
		return Optional.ofNullable(PASSIVES.get(id));
	}

	/**
	 * Look up a passive by id. If absent, prints a warning (matching
	 * AbilityRegistry's style) so silently-dropped passive references in config
	 * become visible.
	 */
	public static Optional<SetPassive> getOrWarn(String id, String context) {
		SetPassive passive = PASSIVES.get(id);
		if (passive == null) {
			System.out.println("Couldn't find SetPassive with id: " + id + " (" + context + ")");
		}
		return Optional.ofNullable(passive);
	}

	public static Map<String, SetPassive> all() {
		return new HashMap<>(PASSIVES);
	}

	/**
	 * Test support: clear all registered passives.
	 */
	public static void clear() {
		PASSIVES.clear();
	}
}
