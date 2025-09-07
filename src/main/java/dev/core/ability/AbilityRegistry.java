package dev.core.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.core.ability.impl.ParticleTestAbility;
import dev.core.ability.impl.SwingBoneAbility;

public class AbilityRegistry {

	private static final Map<String, Ability> ABILITIES = new HashMap<>();

	public static void preregister() {
		register(new ParticleTestAbility());
		register(new SwingBoneAbility());
	}

	private static void register(Ability ability) {
		ABILITIES.put(ability.getId(), ability);
	}

	public static Optional<Ability> get(String id) {
		return Optional.ofNullable(ABILITIES.get(id));
	}

	public static Map<String, Ability> all() {
		return new HashMap<>(ABILITIES);
	}

	public static void updateAll(Map<String, Ability> abilities) {
		ABILITIES.putAll(abilities);
	}

}
