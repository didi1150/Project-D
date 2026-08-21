package dev.core.ability.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityCost;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.CooldownScope;
import dev.core.ability.CooldownScaling;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public class AbilityLoader {

	public static Map<String, Ability> loadAll(ConfigProvider provider) {
		Map<String, Ability> abilities = new HashMap<>();
		ConfigSection root = provider.getRoot().getSection("abilities");

		for (String id : root.getKeys()) {
			load(id, root.getSection(id)).ifPresentOrElse(ability -> {
				abilities.put(id, ability);
			}, () -> {
				System.out.println("Couldn't find Ability with id: " + id);
			});
		}

		return abilities;
	}

	private static Optional<Ability> load(String id, ConfigSection section) {

		Ability ability = AbilityRegistry.get(id).orElse(null);
		if (ability == null) {
			return Optional.empty();
		}
		String name = section.getString("name", id);
		ability.setName(name);
		List<String> description = section.getStringList("description");
		ability.setDescription(description);
		AbilityTriggerType abilityTriggerType = AbilityTriggerType
				.valueOf(section.getString("triggerType", AbilityTriggerType.MANUAL.name()));
		ability.setTriggerType(abilityTriggerType);
		AbilityAction abilityAction = AbilityAction.valueOf(section.getString("action", AbilityAction.NONE.name()));
		ability.setAction(abilityAction);
		CooldownScope cooldownScope = CooldownScope
				.valueOf(section.getString("cooldownScope", CooldownScope.PLAYER.name()));
		ability.setScope(cooldownScope);
		long cooldown = section.getInt("cooldown", 0);
		ability.setCooldown(cooldown);
		CooldownScaling cooldownScaling = CooldownScaling
				.valueOf(section.getString("cooldownScaling", CooldownScaling.HASTE.name()));
		ability.setCooldownScaling(cooldownScaling);
		ability.setTargetsPlayer(section.getBoolean("targetsPlayer", true));

		// Cost: config-only (Java never defines costs). Optional
		// "cost: { mode, amount | formula }" section; an ability without one
		// is free to cast. A malformed cost logs a warning and leaves the
		// ability without a cost.
		ConfigSection costSection = section.getSection("cost");
		if (costSection != null) {
			try {
				ability.setCost(AbilityCost.fromConfig(costSection));
			} catch (IllegalArgumentException e) {
				System.out.println("Bad cost config for ability " + id + ": " + e.getMessage());
			}
		}

		return Optional.of(ability);
	}

}
