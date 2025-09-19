package dev.core.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatTarget;
import dev.core.stat.StatType;
import dev.core.stat.modifier.ModifierStackPolicy;
import dev.core.stat.modifier.ModifierType;
import dev.core.stat.modifier.StatModifier;

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
    private final RPGClassType rpgClassType;
    private final int unlockLevel;

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
        this.rpgClassType = builder.rpgClassType;
        this.unlockLevel = builder.unlockLevel;
    }

    public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities) {
        this(id, name, material, equipmentSlot, null, passiveStats, activeStats, abilities, new ArrayList<>(),
                RPGClassType.NONE, 0);
    }

    public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities,
            List<String> description) {
        this(id, name, material, equipmentSlot, itemSet, passiveStats, activeStats, abilities, description,
                RPGClassType.NONE, 0);
    }

    public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities,
            List<String> description, RPGClassType rpgClassType, int unlockLevel) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.material = material != null ? material : "STONE";
        this.passiveStats = passiveStats != null ? new ArrayList<>(passiveStats) : new ArrayList<>();
        this.activeStats = activeStats != null ? new ArrayList<>(activeStats) : new ArrayList<>();
        this.abilities = abilities != null ? new ArrayList<>(abilities) : new ArrayList<>();
        this.equipmentSlot = equipmentSlot;
        this.itemSet = Optional.ofNullable(itemSet);
        this.rpgClassType = rpgClassType != null ? rpgClassType : RPGClassType.NONE;
        this.unlockLevel = unlockLevel;
    }

    // Legacy constructors for backward compatibility
    public RPGItem(String id, String name, EquipmentSlot equipmentSlot, List<StatModifier> passiveStats,
            List<StatModifier> activeStats, List<Ability> abilities) {
        this(id, name, "STONE", equipmentSlot, null, passiveStats, activeStats, abilities, new ArrayList<>(),
                RPGClassType.NONE, 0);
    }

    public RPGItem(String id, String name, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities) {
        this(id, name, "STONE", equipmentSlot, itemSet, passiveStats, activeStats, abilities, new ArrayList<>(),
                RPGClassType.NONE, 0);
    }

    public static Builder builder(String id, String name, EquipmentSlot equipmentSlot) {
        return new Builder(id, name, equipmentSlot);
    }

    public static Builder builder(String id, String name, String material, EquipmentSlot equipmentSlot) {
        return new Builder(id, name, material, equipmentSlot);
    }

    /**
     * Serializes this RPGItem to a Map for configuration storage
     */
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();

        // Basic fields
        data.put("name", name);
        data.put("material", material);
        data.put("slot", equipmentSlot.name());
        data.put("rpgClassType", rpgClassType.name());
        data.put("unlockLevel", unlockLevel);

        // Description
        if (!description.isEmpty()) {
            data.put("description", new ArrayList<>(description));
        }

        // Passive stats
        List<Map<String, Object>> passiveData = new ArrayList<>();
        for (StatModifier sm : passiveStats) {
            Map<String, Object> modMap = new HashMap<>();
            modMap.put("amount", sm.amount);
            modMap.put("policy", sm.stackPolicy.name());
            modMap.put("modifierType", sm.modifierType.name());
            modMap.put("statType", sm.statType.name());
            modMap.put("statTarget", sm.statTarget.name());
            passiveData.add(modMap);
        }
        if (!passiveData.isEmpty()) {
            data.put("passive-stats", passiveData);
        }

        // Active stats
        List<Map<String, Object>> activeData = new ArrayList<>();
        for (StatModifier sm : activeStats) {
            Map<String, Object> modMap = new HashMap<>();
            modMap.put("amount", sm.amount);
            modMap.put("policy", sm.stackPolicy.name());
            modMap.put("modifierType", sm.modifierType.name());
            modMap.put("statType", sm.statType.name());
            modMap.put("statTarget", sm.statTarget.name());
            activeData.add(modMap);
        }
        if (!activeData.isEmpty()) {
            data.put("active-stats", activeData);
        }

        // Abilities
        List<String> abilityIds = abilities.stream().map(Ability::getId).toList();
        if (!abilityIds.isEmpty()) {
            data.put("abilities", abilityIds);
        }

        // Item set
        if (itemSet.isPresent()) {
            data.put("itemSet", itemSet.get().getId());
        }

        return data;
    }

    /**
     * Deserializes an RPGItem from a Map of configuration data
     */
    @SuppressWarnings("unchecked")
    public static RPGItem deserialize(String id, Map<String, Object> data) {
        Builder builder = RPGItem.builder(id, (String) data.getOrDefault("name", id),
                EquipmentSlot.valueOf((String) data.getOrDefault("slot", "MAIN_HAND")));

        // Material
        if (data.containsKey("material")) {
            builder.withMaterial((String) data.get("material"));
        }

        // RPG Class Type
        if (data.containsKey("rpgClassType")) {
            builder.withRpgClassType(RPGClassType.valueOf((String) data.get("rpgClassType")));
        }

        // Unlock Level
        if (data.containsKey("unlockLevel")) {
            Object unlockLevelObj = data.get("unlockLevel");
            if (unlockLevelObj instanceof Number) {
                builder.withUnlockLevel(((Number) unlockLevelObj).intValue());
            }
        }

        // Description
        if (data.containsKey("description")) {
            List<String> desc = (List<String>) data.get("description");
            builder.withDescription(desc);
        }

        // Passive stats
        if (data.containsKey("passive-stats")) {
            List<Map<String, Object>> passiveData = (List<Map<String, Object>>) data.get("passive-stats");
            List<StatModifier> passiveStats = new ArrayList<>();
            for (Map<String, Object> modData : passiveData) {
                StatModifier modifier = deserializeStatModifier(id, modData);
                passiveStats.add(modifier);
            }
            builder.withPassiveStats(passiveStats);
        }

        // Active stats
        if (data.containsKey("active-stats")) {
            List<Map<String, Object>> activeData = (List<Map<String, Object>>) data.get("active-stats");
            List<StatModifier> activeStats = new ArrayList<>();
            for (Map<String, Object> modData : activeData) {
                StatModifier modifier = deserializeStatModifier(id, modData);
                activeStats.add(modifier);
            }
            builder.withActiveStats(activeStats);
        }

        // Abilities
        if (data.containsKey("abilities")) {
            List<String> abilityIds = (List<String>) data.get("abilities");
            List<Ability> abilities = new ArrayList<>();
            for (String abilityId : abilityIds) {
                AbilityRegistry.get(abilityId).ifPresent(abilities::add);
            }
            builder.withAbilities(abilities);
        }

        // Item Set
        if (data.containsKey("itemSet")) {
//            String itemSetId = (String) data.get("itemSet");
            // Note: You'll need to implement RPGItemSetRegistry.get(itemSetId) similar to
            // AbilityRegistry
            // For now, we'll leave this null
        }

        return builder.build();
    }

    private static StatModifier deserializeStatModifier(String sourceId, Map<String, Object> data) {
        double amount = ((Number) data.getOrDefault("amount", 0)).doubleValue();
        ModifierStackPolicy policy = ModifierStackPolicy.valueOf((String) data.getOrDefault("policy", "STACK"));
        ModifierType modifierType = ModifierType.valueOf((String) data.getOrDefault("modifierType", "FLAT"));
        StatType statType = StatType.valueOf((String) data.getOrDefault("statType", StatType.ATTACK_DAMAGE.name()));
        StatTarget statTarget = StatTarget.valueOf((String) data.getOrDefault("statTarget", "BOTH"));

        return new StatModifier(amount, policy, modifierType, statType, sourceId, -1, System.currentTimeMillis(),
                statTarget);
    }

    // Getters
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

    public RPGClassType getRpgClassType() {
        return rpgClassType;
    }

    public int getUnlockLevel() {
        return unlockLevel;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        RPGItem rpgItem = (RPGItem) obj;
        return Objects.equals(id, rpgItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
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
        private RPGClassType rpgClassType = RPGClassType.NONE;
        private int unlockLevel = 0;

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

        public Builder withRpgClassType(RPGClassType rpgClassType) {
            this.rpgClassType = rpgClassType != null ? rpgClassType : RPGClassType.NONE;
            return this;
        }

        public Builder withUnlockLevel(int unlockLevel) {
            this.unlockLevel = unlockLevel;
            return this;
        }

        public RPGItem build() {
            return new RPGItem(this);
        }
    }
}