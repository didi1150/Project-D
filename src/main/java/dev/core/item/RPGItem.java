package dev.core.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.core.ability.Ability;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatModifier;

public class RPGItem {

	private final String id;
	private final String name;
	private final String material;
	private final List<String> description;
	private final List<StatModifier> passiveStats;
	private final List<StatModifier> activeStats;
	private final List<Ability> abilities;
	private final EquipmentSlot equipmentSlot;
	private final Optional<RPGItemSet> itemSet;

	private RPGItem(Builder builder) {
		this.id = builder.id;
		this.name = builder.name;
		this.material = builder.material;
		this.passiveStats = new ArrayList<>(builder.passiveStats);
		this.activeStats = new ArrayList<>(builder.activeStats);
		this.abilities = new ArrayList<>(builder.abilities);
		this.equipmentSlot = builder.equipmentSlot;
		this.itemSet = Optional.ofNullable(builder.itemSet);
		this.description = new ArrayList<String>(builder.description);
	}

	public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot,
			List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities) {
		this(id, name, material, equipmentSlot, null, passiveStats, activeStats, abilities, new ArrayList<>());
	}

	public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
			List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities,
			List<String> description) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.material = material != null ? material : "STONE";
		this.passiveStats = passiveStats != null ? new ArrayList<>(passiveStats) : new ArrayList<>();
		this.activeStats = activeStats != null ? new ArrayList<>(activeStats) : new ArrayList<>();
		this.abilities = abilities != null ? new ArrayList<>(abilities) : new ArrayList<>();
		this.equipmentSlot = equipmentSlot;
		this.itemSet = Optional.ofNullable(itemSet);
	}

	// Legacy constructors for backward compatibility
	public RPGItem(String id, String name, EquipmentSlot equipmentSlot, List<StatModifier> passiveStats,
			List<StatModifier> activeStats, List<Ability> abilities) {
		this(id, name, "STONE", equipmentSlot, null, passiveStats, activeStats, abilities, new ArrayList<>());
	}

	public RPGItem(String id, String name, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
			List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities) {
		this(id, name, "STONE", equipmentSlot, itemSet, passiveStats, activeStats, abilities, new ArrayList<>());
	}

	public static Builder builder(String id, String name, EquipmentSlot equipmentSlot) {
		return new Builder(id, name, equipmentSlot);
	}

	public static Builder builder(String id, String name, String material, EquipmentSlot equipmentSlot) {
		return new Builder(id, name, material, equipmentSlot);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getMaterial() {
		return material;
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

	public EquipmentSlot getEquipmentSlot() {
		return equipmentSlot;
	}

	public Optional<RPGItemSet> getItemSet() {
		return itemSet;
	}

	public List<String> getDescription() {
		return description;
	}

	public static class Builder {
		private final String id;
		private final String name;
		private final EquipmentSlot equipmentSlot;
		private String material = "STONE";
		private List<StatModifier> passiveStats = new ArrayList<>();
		private List<StatModifier> activeStats = new ArrayList<>();
		private List<Ability> abilities = new ArrayList<>();
		private RPGItemSet itemSet;
		private List<String> description = new ArrayList<String>();

		public Builder(String id, String name, EquipmentSlot equipmentSlot) {
			this.id = id;
			this.name = name;
			this.equipmentSlot = equipmentSlot;
		}

		public Builder(String id, String name, String material, EquipmentSlot equipmentSlot) {
			this.id = id;
			this.name = name;
			this.material = material != null ? material : "STONE";
			this.equipmentSlot = equipmentSlot;
		}

		public Builder withMaterial(String material) {
			this.material = material != null ? material : "STONE";
			return this;
		}

		public Builder withDescription(List<String> description) {
			if (description != null) {
				this.description = new ArrayList<>(description);
			}
			return this;
		}

		public Builder addDescriptionLine(String line) {
			if (line != null) {
				this.description.add(line);
			}
			return this;
		}

		public Builder withPassiveStats(List<StatModifier> passiveStats) {
			if (passiveStats != null) {
				this.passiveStats = new ArrayList<>(passiveStats);
			}
			return this;
		}

		public Builder addPassiveStat(StatModifier statModifier) {
			if (statModifier != null) {
				this.passiveStats.add(statModifier);
			}
			return this;
		}

		public Builder withActiveStats(List<StatModifier> activeStats) {
			if (activeStats != null) {
				this.activeStats = new ArrayList<>(activeStats);
			}
			return this;
		}

		public Builder addActiveStat(StatModifier statModifier) {
			if (statModifier != null) {
				this.activeStats.add(statModifier);
			}
			return this;
		}

		public Builder withAbilities(List<Ability> abilities) {
			if (abilities != null) {
				this.abilities = new ArrayList<>(abilities);
			}
			return this;
		}

		public Builder addAbility(Ability ability) {
			if (ability != null) {
				this.abilities.add(ability);
			}
			return this;
		}

		public Builder withItemSet(RPGItemSet itemSet) {
			this.itemSet = itemSet;
			return this;
		}

		public RPGItem build() {
			return new RPGItem(this);
		}
	}
}