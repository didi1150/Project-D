package dev.core.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.core.ability.Ability;
import dev.core.stat.StatModifier;

public class RPGItem {

    private final String id;
    private final String name;
    private final List<StatModifier> passiveStats;
    private final List<StatModifier> activeStats;
    private final List<Ability> abilities;
    private final EquipmentSlot equipmentSlot;
    private final Optional<RPGItemSet> itemSet;

    private RPGItem(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.passiveStats = new ArrayList<>(builder.passiveStats);
        this.activeStats = new ArrayList<>(builder.activeStats);
        this.abilities = new ArrayList<>(builder.abilities);
        this.equipmentSlot = builder.equipmentSlot;
        this.itemSet = Optional.ofNullable(builder.itemSet);
    }

    public RPGItem(String id, String name, EquipmentSlot equipmentSlot, List<StatModifier> passiveStats,
            List<StatModifier> activeStats, List<Ability> abilities) {
        this(id, name, equipmentSlot, null, passiveStats, activeStats, abilities);
    }

    public RPGItem(String id, String name, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities) {
        this.id = id;
        this.name = name;
        this.passiveStats = passiveStats != null ? new ArrayList<>(passiveStats) : new ArrayList<>();
        this.activeStats = activeStats != null ? new ArrayList<>(activeStats) : new ArrayList<>();
        this.abilities = abilities != null ? new ArrayList<>(abilities) : new ArrayList<>();
        this.equipmentSlot = equipmentSlot;
        this.itemSet = Optional.ofNullable(itemSet);
    }

    public static Builder builder(String id, String name, EquipmentSlot equipmentSlot) {
        return new Builder(id, name, equipmentSlot);
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

    public EquipmentSlot getEquipmentSlot() {
        return equipmentSlot;
    }

    public Optional<RPGItemSet> getItemSet() {
        return itemSet;
    }

    public static class Builder {
        private final String id;
        private final String name;
        private final EquipmentSlot equipmentSlot;
        private List<StatModifier> passiveStats = new ArrayList<>();
        private List<StatModifier> activeStats = new ArrayList<>();
        private List<Ability> abilities = new ArrayList<>();
        private RPGItemSet itemSet;

        public Builder(String id, String name, EquipmentSlot equipmentSlot) {
            this.id = id;
            this.name = name;
            this.equipmentSlot = equipmentSlot;
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