package dev.core.entity.mob;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatManager;

/**
 * Data-driven definition of a dungeon mob, loaded from {@code dungeon-mobs.yml}.
 * Every mob is a full RPG entity with its own {@link StatManager}, a display name,
 * and an optional main weapon (a vanilla material for looks, or an items.yml id
 * that equips stats + abilities).
 */
public class MobDefinition {

    private final String id;
    private final String entityType;
    private final int weight;
    private final Set<SpawnTier> tiers;
    private final String displayName;
    private final StatManager baseStats;
    private final String weaponMaterial;
    private final String mainHandItemId;
    private final double abilityDamageMultiplier;
    private final int abilityCastInterval;
    private final boolean miniBoss;
    private final boolean bossBar;
    private final Map<EquipmentSlot, String> armor;
    private final List<MobEffect> effects;

    public MobDefinition(String id, String entityType, int weight, Set<SpawnTier> tiers, String displayName,
            StatManager baseStats, String weaponMaterial, String mainHandItemId, double abilityDamageMultiplier,
            int abilityCastInterval, boolean miniBoss, boolean bossBar, Map<EquipmentSlot, String> armor,
            List<MobEffect> effects) {
        this.id = id;
        this.entityType = entityType;
        this.weight = weight;
        this.tiers = tiers == null || tiers.isEmpty() ? Set.of(SpawnTier.values()) : Set.copyOf(tiers);
        this.displayName = displayName;
        this.baseStats = baseStats != null ? baseStats : new StatManager(null);
        this.weaponMaterial = weaponMaterial;
        this.mainHandItemId = mainHandItemId;
        this.abilityDamageMultiplier = abilityDamageMultiplier;
        this.abilityCastInterval = Math.max(1, abilityCastInterval);
        this.miniBoss = miniBoss;
        this.bossBar = bossBar;
        this.armor = armor == null ? Map.of() : Map.copyOf(armor);
        this.effects = effects == null ? List.of() : List.copyOf(effects);
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

    /** Vanilla main-hand material (cosmetic); {@code null} for none. */
    public String getWeaponMaterial() {
        return weaponMaterial;
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

    /** Whether a boss bar should be shown for this mob's health. */
    public boolean isBossBar() {
        return bossBar;
    }

    /** RPG item ids per armor slot ({@code HEAD}/{@code CHEST}/{@code LEGS}/{@code FEET}). */
    public Map<EquipmentSlot, String> getArmor() {
        return armor;
    }

    public List<MobEffect> getEffects() {
        return effects;
    }
}