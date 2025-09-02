package dev.core.item;

import java.util.ArrayList;
import java.util.List;

import dev.core.ability.Ability;
import dev.core.stat.StatModifier;

public class RPGItem {

	private final String id;
	private final String name;
	private final List<StatModifier> passiveStats;
	private final List<StatModifier> activeStats;
	private final List<Ability> abilities;

	public RPGItem(String id, String name) {
		this.id = id;
		this.name = name;
		this.passiveStats = new ArrayList<StatModifier>();
		this.activeStats = new ArrayList<StatModifier>();
		this.abilities = new ArrayList<>();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public List<StatModifier> getPassiveStats() {
		return new ArrayList<>(passiveStats);
	}

	public List<StatModifier> getActiveStats() {
		return new ArrayList<>(activeStats);
	}

	public List<Ability> getAbilities() {
		return new ArrayList<>(abilities);
	}

	public void addPassiveStat(StatModifier statModifier) {
		passiveStats.add(statModifier);
	}

	public void addActiveStat(StatModifier statModifier) {
		activeStats.add(statModifier);
	}

	public void addAbility(Ability ability) {
		abilities.add(ability);
	}

}
