package dev.core.ability;

import java.util.List;

import dev.core.entity.RPGEntity;
import dev.core.stat.StatModifier;

public class SetBonus {
	private final String description;
	private final List<StatModifier> statModifiers;
	private final List<Ability> abilities;

	public SetBonus(String description, List<StatModifier> statModifiers, List<Ability> abilities) {
		this.description = description;
		this.statModifiers = statModifiers;
		this.abilities = abilities;
	}

	public String getDescription() {
		return description;
	}

	public void apply(RPGEntity entity) {
		statModifiers.forEach(entity.getStatManager()::addStatModifier);
		abilities.forEach(ability -> entity.getEquipmentManager().addTemporaryAbility(ability));
	}

	public void remove(RPGEntity entity) {
		statModifiers.forEach(entity.getStatManager()::removeStatModifier);
		abilities.forEach(ability -> entity.getEquipmentManager().removeTemporaryAbility(ability));
	}
}
