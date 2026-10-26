package dev.core.entity.mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.loader.StatLoader;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Loads {@link MobDefinition}s from {@code dungeon-mobs.yml}:
 *
 * <pre>
 * dungeon-mobs:
 *   basic-zombie:
 *     entity-type: ZOMBIE
 *     weight: 10
 *     tiers: [BASIC, ADVANCED]
 *     display-name: "&7Dungeon Zombie"
 *     base-health: 20
 *     base-attack-damage: 5
 *     base-movement-speed: 0.23
 *     base-armor: 0
 *     main-hand-item: BONEMERANG
 *     armor:
 *       chest: SOME_CHESTPLATE
 *     ability-damage-multiplier: 0.5
 *     mini-boss: false
 *     effects:
 *       - BONE_SWING
 * </pre>
 */
public class MobDefinitionLoader {

    public static Map<String, MobDefinition> loadAll(ConfigProvider provider) {
        Map<String, MobDefinition> result = new LinkedHashMap<>();
        ConfigSection root = provider.getRoot().getSection("dungeon-mobs");
        if (root == null) {
            return result;
        }
        for (String id : root.getKeys()) {
            MobDefinition definition = load(id, root.getSection(id));
            if (definition != null) {
                result.put(id, definition);
            }
        }
        return result;
    }

    private static MobDefinition load(String id, ConfigSection section) {
        String entityType = section.getString("entity-type", "");
        int weight = Math.max(1, section.getInt("weight", 10));
        Set<SpawnTier> tiers = parseTiers(section.getStringList("tiers"));
        String displayName = section.getString("display-name", null);
        StatManager baseStats = new StatManager(StatLoader.loadStats(section.getSection("stats")));
        if (baseStats.getCurrentValue(StatType.HEALTH_MAX, System.currentTimeMillis()) <= 0) {
            System.out.println("Mob definition '" + id + "' has no HEALTH_MAX in its stats block ("
                    + section.getString("entity-type", "unknown entity-type") + "); skipping it. "
                    + "Check the `stats` section of dungeon-mobs.yml.");
            return null;
        }
        String mainHandItemId = section.getString("main-hand-item", null);
        double abilityDamageMultiplier = section.getDouble("ability-damage-multiplier", 1.0);
        int abilityCastInterval = section.getInt("ability-cast-interval", 80);
        boolean miniBoss = section.getBoolean("mini-boss", false);
        String behaviorId = section.getString("behavior", null);
        ConfigSection behaviorParams = section.getSection("behavior-params");
        Map<EquipmentSlot, String> armor = loadArmor(section);
        List<MobEffect> effects = loadEffects(section);
        Set<Integer> floors = parseFloors(section);

        return new MobDefinition(id, entityType, weight, tiers, displayName, baseStats, mainHandItemId,
                abilityDamageMultiplier, abilityCastInterval, miniBoss, behaviorId, behaviorParams, armor, effects,
                floors);
    }

    private static Map<EquipmentSlot, String> loadArmor(ConfigSection section) {
        Map<EquipmentSlot, String> armor = new HashMap<>();
        ConfigSection armorSection = section.getSection("armor");
        for (String slotKey : armorSection.getKeys()) {
            try {
                armor.put(EquipmentSlot.valueOf(slotKey.toUpperCase()), armorSection.getString(slotKey, null));
            } catch (IllegalArgumentException ignored) {
                System.out.println("Unknown armor slot '" + slotKey + "' in mob definition; ignored.");
            }
        }
        return armor;
    }

    private static Set<SpawnTier> parseTiers(List<String> tierNames) {
        if (tierNames == null || tierNames.isEmpty()) {
            return EnumSet.allOf(SpawnTier.class);
        }
        Set<SpawnTier> tiers = EnumSet.noneOf(SpawnTier.class);
        for (String name : tierNames) {
            try {
                tiers.add(SpawnTier.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                System.out.println("Unknown spawn tier '" + name + "' in mob definition; ignored.");
            }
        }
        return tiers.isEmpty() ? EnumSet.allOf(SpawnTier.class) : tiers;
    }

    private static List<MobEffect> loadEffects(ConfigSection section) {
        List<MobEffect> effects = new ArrayList<>();
        for (String effectId : section.getStringList("effects")) {
            if (effectId == null || effectId.isBlank()) {
                continue;
            }
            effects.add(new MobEffect(effectId.trim()));
        }
        return effects;
    }

    private static Set<Integer> parseFloors(ConfigSection section) {
        // Supports: floors: [1,2]  or  floors: 1  or  floors: "1,2"  or absent (global)
        // Reads string list, integer list via string conversion, and single int fallback.
        List<String> list = section.getStringList("floors");
        Set<Integer> out = new java.util.LinkedHashSet<>();
        if (list != null && !list.isEmpty()) {
            for (String s : list) {
                if (s == null || s.isBlank()) continue;
                // Allow comma-separated inside one entry
                for (String part : s.split(",")) {
                    try {
                        out.add(Integer.parseInt(part.trim()));
                    } catch (NumberFormatException ignored) {
                        System.out.println("Unknown floors entry '" + part + "' in mob definition; ignored.");
                    }
                }
            }
            return out;
        }
        // Try single int fallback via getInt with sentinel
        // We distinguish missing vs explicit 0 by checking if key exists
        if (section.getKeys().contains("floors")) {
            // Could be a single number stored as int
            try {
                int single = section.getInt("floors", Integer.MIN_VALUE);
                if (single != Integer.MIN_VALUE) {
                    out.add(single);
                    return out;
                }
            } catch (Exception ignored) {
            }
            // Could be a string "1"
            String singleStr = section.getString("floors", null);
            if (singleStr != null && !singleStr.isBlank()) {
                for (String part : singleStr.split(",")) {
                    try {
                        out.add(Integer.parseInt(part.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return out;
    }
}
