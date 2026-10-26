package dev.core.entity.mob;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatManager;
import dev.core.storage.config.ConfigSection;

/**
 * Data-driven definition of a dungeon mob, loaded from {@code dungeon-mobs.yml}.
 * Every mob is a full RPG entity with its own {@link StatManager}, a display name,
 * and an optional main-hand weapon and armor that must reference items.yml ids so
 * the mob gains the item's stats + abilities.
 */
public class MobDefinition {

    private final String id;
    private final String entityType;
    private final int weight;
    private final Set<SpawnTier> tiers;
    private final String displayName;
    private final StatManager baseStats;
    private final String mainHandItemId;
    private final double abilityDamageMultiplier;
    private final int abilityCastInterval;
    private final boolean miniBoss;
    private final String behaviorId;
    private final ConfigSection behaviorParams;
    private final Map<EquipmentSlot, String> armor;
    private final List<MobEffect> effects;
    private final Set<Integer> floors;

    public MobDefinition(String id, String entityType, int weight, Set<SpawnTier> tiers, String displayName,
            StatManager baseStats, String mainHandItemId, double abilityDamageMultiplier,
            int abilityCastInterval, boolean miniBoss, String behaviorId, ConfigSection behaviorParams,
            Map<EquipmentSlot, String> armor, List<MobEffect> effects) {
        this(id, entityType, weight, tiers, displayName, baseStats, mainHandItemId, abilityDamageMultiplier,
                abilityCastInterval, miniBoss, behaviorId, behaviorParams, armor, effects, Set.of());
    }

    public MobDefinition(String id, String entityType, int weight, Set<SpawnTier> tiers, String displayName,
            StatManager baseStats, String mainHandItemId, double abilityDamageMultiplier,
            int abilityCastInterval, boolean miniBoss, String behaviorId, ConfigSection behaviorParams,
            Map<EquipmentSlot, String> armor, List<MobEffect> effects, Set<Integer> floors) {
        this.id = id;
        this.entityType = entityType;
        this.weight = weight;
        this.tiers = tiers == null || tiers.isEmpty() ? Set.of(SpawnTier.values()) : Set.copyOf(tiers);
        this.displayName = displayName;
        this.baseStats = baseStats != null ? baseStats : new StatManager(null);
        this.mainHandItemId = mainHandItemId;
        this.abilityDamageMultiplier = abilityDamageMultiplier;
        this.abilityCastInterval = Math.max(1, abilityCastInterval);
        this.miniBoss = miniBoss;
        this.behaviorId = behaviorId;
        this.behaviorParams = behaviorParams;
        this.armor = armor == null ? Map.of() : Map.copyOf(armor);
        this.effects = effects == null ? List.of() : List.copyOf(effects);
        this.floors = floors == null || floors.isEmpty() ? Set.of() : Set.copyOf(floors);
    }

    public String getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public int getWeight() {
        return weight;
    }

    public Set<SpawnTier> getTiers() {
        return tiers;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The mob's base RPG stats (a fresh copy is taken per spawn). */
    public StatManager getBaseStats() {
        return baseStats;
    }

    /** items.yml id for the mob's main-hand weapon (equips stats + abilities); {@code null} for none. */
    public String getMainHandItemId() {
        return mainHandItemId;
    }

    /** Multiplier applied to ability damage this mob triggers. */
    public double getAbilityDamageMultiplier() {
        return abilityDamageMultiplier;
    }

    /** Ticks between ability casts while a target is in range. */
    public int getAbilityCastInterval() {
        return abilityCastInterval;
    }

    public boolean isMiniBoss() {
        return miniBoss;
    }

    /**
     * Optional id of a Java {@code MobBehavior} (registered in
     * {@code MobBehaviorRegistry}) that drives this mob's custom logic. {@code null}
     * for the default RPG-mob behavior. Typically set on minibosses.
     */
    public String getBehaviorId() {
        return behaviorId;
    }

    public ConfigSection getBehaviorParams() {
        return behaviorParams;
    }

    /** RPG item ids per armor slot ({@code HEAD}/{@code CHEST}/{@code LEGS}/{@code FEET}). */
    public Map<EquipmentSlot, String> getArmor() {
        return armor;
    }

    public List<MobEffect> getEffects() {
        return effects;
    }

    /**
     * Floor restriction: empty means allowed on every floor (global).
     * If non-empty, this definition is only eligible when spawned for one of those floors.
     */
    public Set<Integer> getFloors() {
        return floors;
    }

    public boolean isAllowedForFloor(int floor) {
        return floors.isEmpty() || floors.contains(floor);
    }
}