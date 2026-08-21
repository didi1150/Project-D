package dev.core.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.core.ability.impl.FocusBeamAbility;
import dev.core.ability.impl.ParticleTestAbility;
import dev.core.ability.impl.ShieldBashAbility;
import dev.core.ability.impl.ShadowWeaverStaffAbility;
import dev.core.ability.impl.SmashAbility;
import dev.core.ability.impl.SpinjitzuAbility;
import dev.core.ability.impl.SpiritSceptreAbility;
import dev.core.ability.impl.SwingBoneAbility;

/**
 * Single registry for abilities AND their backing {@link Effect} factories:
 * one {@link #register(Ability, EffectFactory)} call wires both the metadata
 * (name, description, cost, ... optionally overridden by abilities.yml) and
 * the in-game behavior. Factories are keyed by ability id, so
 * {@link #createEffect} is the only lookup the effect manager needs.
 */
public class AbilityRegistry {

	private static final Map<String, Ability> ABILITIES = new HashMap<>();
	private static final Map<String, EffectFactory> FACTORIES = new HashMap<>();

	/**
	 * Test/compat support: registers the built-in abilities without effects
	 * (config-loading tests only need the metadata). Production startup wires
	 * the same abilities with their effects via {@link #register(Ability,
	 * EffectFactory)} (see {@code DMain.onEnable}).
	 */
	public static void preregister() {
		register(new ParticleTestAbility());
		register(new SwingBoneAbility());
		register(new SpiritSceptreAbility());
		register(new FocusBeamAbility());
		register(new SpinjitzuAbility());
		register(new SmashAbility());
		register(new ShieldBashAbility());
		register(ShadowWeaverStaffAbility.place());
		register(ShadowWeaverStaffAbility.dash());
	}

	/**
	 * Registers an ability without an effect binding (casts will warn and do
	 * nothing). Prefer {@link #register(Ability, EffectFactory)}.
	 */
	public static void register(Ability ability) {
		if (ability == null || ability.getId() == null) {
			return;
		}
		ABILITIES.put(ability.getId(), ability);
	}

	/**
	 * Registers an ability together with the effect that backs it - the single
	 * registration point for both halves of an ability.
	 */
	public static void register(Ability ability, EffectFactory effectFactory) {
		register(ability);
		if (ability != null && ability.getId() != null && effectFactory != null) {
			FACTORIES.put(ability.getId(), effectFactory);
		}
	}

	/**
	 * Creates the effect registered for an ability id, or {@code null} with a
	 * warning when the ability has no effect binding.
	 */
	public static Effect createEffect(String abilityId, String cooldownKey) {
		EffectFactory factory = FACTORIES.get(abilityId);
		if (factory == null) {
			System.out.println("No effect registered for ability id: " + abilityId);
			return null;
		}
		return factory.create(cooldownKey);
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
	 * Test support: clear all registered abilities and effects.
	 */
	public static void clear() {
		ABILITIES.clear();
		FACTORIES.clear();
	}

}