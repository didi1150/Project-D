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
 *       - type: SPEED
 *         amplifier: 0
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
            result.put(id, load(id, root.getSection(id)));
        }
        return result;
    }

    private static MobDefinition load(String id, ConfigSection section) {
        String entityType = section.getString("entity-type", "");
        int weight = Math.max(1, section.getInt("weight", 10));
        Set<SpawnTier> tiers = parseTiers(section.getStringList("tiers"));
        String displayName = section.getString("display-name", null);
        StatManager baseStats = new StatManager(StatLoader.loadStats(section.getSection("stats")));
        String mainHandItemId = section.getString("main-hand-item", null);
        double abilityDamageMultiplier = section.getDouble("ability-damage-multiplier", 1.0);
        int abilityCastInterval = section.getInt("ability-cast-interval", 80);
        boolean miniBoss = section.getBoolean("mini-boss", false);
        boolean bossBar = section.getBoolean("boss-bar", false);
        String behaviorId = section.getString("behavior", null);
        Map<EquipmentSlot, String> armor = loadArmor(section);
        List<MobEffect> effects = loadEffects(section.getSectionList("effects"));

        return new MobDefinition(id, entityType, weight, tiers, displayName, baseStats, mainHandItemId,
                abilityDamageMultiplier, abilityCastInterval, miniBoss, bossBar, behaviorId, armor, effects);
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

    private static List<MobEffect> loadEffects(List<ConfigSection> effectSections) {
        List<MobEffect> effects = new ArrayList<>();
        if (effectSections == null) {
            return effects;
        }
        for (ConfigSection section : effectSections) {
            String type = section.getString("type", "");
            if (type.isBlank()) {
                continue;
            }
            int amplifier = section.getInt("amplifier", 0);
            int durationTicks = section.getInt("duration", -1);
            effects.add(new MobEffect(type, amplifier, durationTicks));
        }
        return effects;
    }
}
