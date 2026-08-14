package dev.core.ability;

import java.util.List;
import java.util.UUID;

import dev.core.ability.passive.SetPassive;
import dev.core.entity.RPGEntity;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.provider.adapter.SetBonusStatProvider;

public class SetBonus {
	private final String description;
	private final List<StatModifier> statModifiers;
	private final List<Ability> abilities;
	private final List<SetPassive> passives;
	// Stable per SetBonus instance so apply/remove cycles register and
	// unregister the same StatEngine provider id.
	private final String providerId;

	public SetBonus(String description, List<StatModifier> statModifiers, List<Ability> abilities,
			List<SetPassive> passives) {
		this.description = description;
		this.statModifiers = statModifiers;
		this.abilities = abilities;
		this.passives = passives;
		this.providerId = "setbonus:" + UUID.randomUUID();
	}

	public String getDescription() {
		return description;
	}

	public List<StatModifier> getStatModifiers() {
		return statModifiers;
	}

	public List<SetPassive> getPassives() {
		return passives;
	}

	public void apply(RPGEntity entity) {
		if (!statModifiers.isEmpty()) {
			entity.getStatEngine().registerProvider(new SetBonusStatProvider(providerId, statModifiers));
		}
		abilities.forEach(ability -> entity.getEquipmentManager().addTemporaryAbility(ability));
		passives.forEach(passive -> passive.onApply(entity));
	}

	public void remove(RPGEntity entity) {
		if (!statModifiers.isEmpty()) {
			entity.getStatEngine().unregisterProvider(providerId);
		}
		abilities.forEach(ability -> entity.getEquipmentManager().removeTemporaryAbility(ability));
		passives.forEach(passive -> passive.onRemove(entity));
	}
}
