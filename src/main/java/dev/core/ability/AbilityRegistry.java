package dev.core.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.core.ability.impl.ParticleTestAbility;
import dev.core.ability.impl.SmashAbility;
import dev.core.ability.impl.SpinjitzuAbility;
import dev.core.ability.impl.SpiritSceptreAbility;
import dev.core.ability.impl.SwingBoneAbility;

public class AbilityRegistry {

	private static final Map<String, Ability> ABILITIES = new HashMap<>();

	public static void preregister() {
		register(new ParticleTestAbility());
		register(new SwingBoneAbility());
		register(new SpiritSceptreAbility());
		register(new SpinjitzuAbility());
		register(new SmashAbility());
	}

	public static void register(Ability ability) {
		if (ability == null || ability.getId() == null) {
			return;
		}
		ABILITIES.put(ability.getId(), ability);
	}

	public static Optional<Ability> get(String id) {
		return Optional.ofNullable(ABILITIES.get(id));
	}

	/**
	 * Look up an ability by id. If absent, prints a warning (matching
	 * AbilityLoader's style) so silently-dropped ability references in config
	 * become visible.
	 */
	public static Optional<Ability> getOrWarn(String id, String context) {
		Ability ability = ABILITIES.get(id);
		if (ability == null) {
			System.out.println("Couldn't find Ability with id: " + id + " (" + context + ")");
		}
		return Optional.ofNullable(ability);
	}

	public static Map<String, Ability> all() {
		return new HashMap<>(ABILITIES);
	}

	public static void updateAll(Map<String, Ability> abilities) {
		ABILITIES.putAll(abilities);
	}

	/**
	 * Test support: clear all registered abilities.
	 */
	public static void clear() {
		ABILITIES.clear();
	}

}
