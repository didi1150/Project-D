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
import dev.core.stat.modifier.StatModifierType;
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
    private Optional<RPGItemSet> itemSet;
    private final RPGClassType rpgClassType;
    private final int unlockLevel;
    private final List<RPGClassType> allowedClasses;
    private final ItemUsage usage;
    private final Optional<Integer> leatherColor;
    private final Optional<String> skullOwner;
    private final Optional<String> skullTexture;

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
        this.allowedClasses = builder.allowedClasses == null ? new ArrayList<>() : new ArrayList<>(builder.allowedClasses);
        this.usage = builder.usage;
        this.leatherColor = builder.leatherColor;
        this.skullOwner = builder.skullOwner;
        this.skullTexture = builder.skullTexture;
    }

    public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities) {
        this(builder(id, name, material, equipmentSlot)
            .withPassiveStats(passiveStats)
            .withActiveStats(activeStats)
            .withAbilities(abilities));
    }

    public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities,
            List<String> description) {
        this(builder(id, name, material, equipmentSlot)
            .withItemSet(itemSet)
            .withPassiveStats(passiveStats)
            .withActiveStats(activeStats)
            .withAbilities(abilities)
            .withDescription(description));
    }

    public RPGItem(String id, String name, String material, EquipmentSlot equipmentSlot, RPGItemSet itemSet,
            List<StatModifier> passiveStats, List<StatModifier> activeStats, List<Ability> abilities,
            List<String> description, RPGClassType rpgClassType, int unlockLevel) {
        this(builder(id, name, material, equipmentSlot)
            .withItemSet(itemSet)
            .withPassiveStats(passiveStats)
            .withActiveStats(activeStats)
            .withAbilities(abilities)
            .withDescription(description)
            .withRpgClassType(rpgClassType)
            .withUnlockLevel(unlockLevel));
    }

    // Legacy constructors for backward compatibility - delegate to builder
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
            modMap.put("statModifierType", sm.statModifierType.name());
            modMap.put("statType", sm.statType.name());
            modMap.put("statTarget", sm.statTarget.name());
            modMap.put("priority", sm.priority);
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
            modMap.put("statModifierType", sm.statModifierType.name());
            modMap.put("statType", sm.statType.name());
            modMap.put("statTarget", sm.statTarget.name());
            modMap.put("priority", sm.priority);
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

        // Classification
        if (!allowedClasses.isEmpty()) {
            data.put("allowed-classes", allowedClasses.stream().map(Enum::name).toList());
        }
        if (usage == ItemUsage.MOB_ONLY) {
            data.put("usage", usage.name());
        }

        // Item set
        if (itemSet.isPresent()) {
            data.put("itemSet", itemSet.get().getId());
        }

        // Visual customization
        leatherColor.ifPresent(color -> data.put("leather-color", color));
        skullOwner.ifPresent(owner -> data.put("skull-owner", owner));
        skullTexture.ifPresent(texture -> data.put("skull-texture", texture));

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

        // Visual customization
        if (data.containsKey("leather-color")) {
            Integer rgb = parseRgbColor(data.get("leather-color"));
            if (rgb != null) {
                builder.withLeatherColor(rgb);
            }
        }
        if (data.containsKey("skull-owner")) {
            builder.withSkullOwner(String.valueOf(data.get("skull-owner")));
        }
        if (data.containsKey("skull-texture")) {
            builder.withSkullTexture(String.valueOf(data.get("skull-texture")));
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

        // Classification
        if (data.containsKey("allowed-classes")) {
            List<String> classNames = (List<String>) data.get("allowed-classes");
            List<RPGClassType> classes = new ArrayList<>();
            for (String name : classNames) {
                try {
                    classes.add(RPGClassType.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                }
            }
            builder.withAllowedClasses(classes);
        }
        // Classification: prefer the explicit `usage` key, fall back to legacy `mob-only`.
        ItemUsage usage = ItemUsage.BOTH;
        Object usageRaw = data.get("usage");
        if (usageRaw instanceof String s) {
            try {
                usage = ItemUsage.valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to BOTH, then check the legacy alias below
            }
        }
        if (usage == ItemUsage.BOTH && data.get("mob-only") instanceof Boolean legacy && legacy) {
            usage = ItemUsage.MOB_ONLY;
        }
        builder.usage(usage);

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
                AbilityRegistry.getOrWarn(abilityId, "item " + id).ifPresent(abilities::add);
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

    /**
     * Parses an RGB color from a config value: a plain decimal/hex integer or
     * a {@code "#RRGGBB"} / {@code "0xRRGGBB"} string. Returns null when the
     * value cannot be parsed.
     */
    public static Integer parseRgbColor(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String value = raw.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            if (value.startsWith("#")) {
                return Integer.parseInt(value.substring(1), 16);
            }
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Integer.parseInt(value.substring(2), 16);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static StatModifier deserializeStatModifier(String sourceId, Map<String, Object> data) {
        double amount = ((Number) data.getOrDefault("amount", 0)).doubleValue();
        ModifierStackPolicy policy = ModifierStackPolicy.valueOf((String) data.getOrDefault("policy", "STACK"));
        Object rawType = data.get("statModifierType");
        if (rawType == null) {
            rawType = data.get("modifierType");
        }
        StatModifierType statModifierType = StatModifierType
                .valueOf(rawType != null ? rawType.toString() : "FLAT");
        StatType statType = StatType.valueOf((String) data.getOrDefault("statType", StatType.ATTACK_DAMAGE.name()));
        StatTarget statTarget = StatTarget.valueOf((String) data.getOrDefault("statTarget", "BOTH"));
        int priority = ((Number) data.getOrDefault("priority", 0)).intValue();

        return StatModifier.builder(amount, statModifierType, statType, sourceId)
            .stackPolicy(policy)
            .statTarget(statTarget)
            .priority(priority)
            .build();
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

    /**
     * RGB tint for leather armor pieces (see {@link #getLeatherColor()}). Empty
     * when the item's material is not leather armor or no tint is configured.
     */
    public Optional<Integer> getLeatherColor() {
        return leatherColor;
    }

    /**
     * Player name/UUID whose head skin is shown on a {@code PLAYER_HEAD} item.
     * Empty when the item is not a head or no owner is configured.
     */
    public Optional<String> getSkullOwner() {
        return skullOwner;
    }

    /**
     * Base64 {@code textures} value for a fully custom {@code PLAYER_HEAD} skin.
     * Takes precedence over {@link #getSkullOwner()}. Empty when not configured.
     */
    public Optional<String> getSkullTexture() {
        return skullTexture;
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

    /**
     * Attaches the item set this piece belongs to (loaded from the
     * {@code item-sets} section). Once equipped alongside enough other pieces,
     * the set's bonus applies.
     */
    public void setItemSet(RPGItemSet itemSet) {
        this.itemSet = Optional.ofNullable(itemSet);
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

    /**
     * Classes that may use this item. Empty means any class. {@code mobOnly}
     * items are never usable by players regardless of this list.
     */
    public List<RPGClassType> getAllowedClasses() {
        return new ArrayList<>(allowedClasses);
    }

    /** What this item is usable for: both players and mobs, or mobs only. */
    public ItemUsage getUsage() {
        return usage;
    }

    /** Whether this item is exclusively for mobs (players can't select/equip it). */
    public boolean isMobOnly() {
        return usage == ItemUsage.MOB_ONLY;
    }

    /**
     * Whether a player of the given class may select this item. Used only by the
     * item draft menu (SelectItemState) to list a class's items; it filters out
     * {@code mob-only} items and applies the {@code allowed-classes} lock. This is
     * a player-only concern — mobs equip items purely by id and never call this.
     */
    public boolean isAllowedForClass(RPGClassType classType) {
        return usage != ItemUsage.MOB_ONLY && (allowedClasses.isEmpty() || allowedClasses.contains(classType));
    }

    /** Whether this item occupies an armor slot (HEAD, CHEST, LEGS or FEET). */
    public boolean isArmor() {
        return equipmentSlot == EquipmentSlot.HEAD || equipmentSlot == EquipmentSlot.CHEST
                || equipmentSlot == EquipmentSlot.LEGS || equipmentSlot == EquipmentSlot.FEET;
    }

    /**
     * Whether a player at the given level may obtain this item. An {@code
     * unlockLevel} of 0 (or an absent config value) means no level requirement.
     */
    public boolean isUsableAtLevel(int level) {
        return unlockLevel <= 0 || level >= unlockLevel;
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
        private List<RPGClassType> allowedClasses = new ArrayList<>();
        private ItemUsage usage = ItemUsage.BOTH;
        private Optional<Integer> leatherColor = Optional.empty();
        private Optional<String> skullOwner = Optional.empty();
        private Optional<String> skullTexture = Optional.empty();

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

        public Builder withAllowedClasses(List<RPGClassType> allowedClasses) {
            if (allowedClasses != null) {
                this.allowedClasses = new ArrayList<>(allowedClasses);
            }
            return this;
        }

        public Builder mobOnly(boolean mobOnly) {
            this.usage = mobOnly ? ItemUsage.MOB_ONLY : ItemUsage.BOTH;
            return this;
        }

        public Builder usage(ItemUsage usage) {
            this.usage = usage != null ? usage : ItemUsage.BOTH;
            return this;
        }

        /** Tints a leather armor item (e.g. {@code 0x8B0000}) dark red. */
        public Builder withLeatherColor(int rgb) {
            this.leatherColor = Optional.of(rgb);
            return this;
        }

        /** Clears the leather tint when passed null. */
        public Builder withLeatherColor(Integer rgb) {
            this.leatherColor = rgb == null ? Optional.empty() : Optional.of(rgb);
            return this;
        }

        /** Shows a player's head skin on a {@code PLAYER_HEAD} item. */
        public Builder withSkullOwner(String owner) {
            this.skullOwner = owner == null || owner.isBlank() ? Optional.empty() : Optional.of(owner);
            return this;
        }

        /** Applies a base64 {@code textures} value as the item's head skin. */
        public Builder withSkullTexture(String base64Texture) {
            this.skullTexture = base64Texture == null || base64Texture.isBlank() ? Optional.empty()
                    : Optional.of(base64Texture);
            return this;
        }

        public RPGItem build() {
            return new RPGItem(this);
        }
    }
}