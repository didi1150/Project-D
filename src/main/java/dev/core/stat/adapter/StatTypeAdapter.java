package dev.core.stat.adapter;

import java.util.HashMap;
import java.util.Map;
import dev.core.item.display.TextColor;
import dev.core.stat.StatType;
import dev.core.stat.descriptor.StatDescriptor;
import dev.core.stat.descriptor.StatRegistry;

/**
 * Adapter that registers StatType enum values into the new StatRegistry.
 * Maintains backward compatibility with existing code.
 * 
 * Call StatTypeAdapter.initializeStatTypes() at startup.
 */
public class StatTypeAdapter {

    private static final Map<StatType, String> TYPE_TO_ID = new HashMap<>();

    static {
        // Map enum to registry ids
        TYPE_TO_ID.put(StatType.ATTACK_DAMAGE, "core:attack_damage");
        TYPE_TO_ID.put(StatType.ABILITY_POWER, "core:ability_power");
        TYPE_TO_ID.put(StatType.ARMOR, "core:armor");
        TYPE_TO_ID.put(StatType.MAGIC_RESIST, "core:magic_resist");
        TYPE_TO_ID.put(StatType.ATTACK_SPEED, "core:attack_speed");
        TYPE_TO_ID.put(StatType.MOVE_SPEED, "core:move_speed");
        TYPE_TO_ID.put(StatType.ABILITY_HASTE, "core:ability_haste");
        TYPE_TO_ID.put(StatType.LETHALITY, "core:lethality");
        TYPE_TO_ID.put(StatType.ARMOR_PENETRATION, "core:armor_penetration");
        TYPE_TO_ID.put(StatType.CRIT_CHANCE, "core:crit_chance");
        TYPE_TO_ID.put(StatType.PROJECTILE_DAMAGE, "core:projectile_damage");
        TYPE_TO_ID.put(StatType.HEAL_AND_SHIELD_POWER, "core:heal_and_shield_power");
        TYPE_TO_ID.put(StatType.HEALTH_MAX, "core:health_max");
        TYPE_TO_ID.put(StatType.HEALTH_RESOURCE, "core:health");
        TYPE_TO_ID.put(StatType.HEALTH_REGEN, "core:health_regen");
        TYPE_TO_ID.put(StatType.MANA_MAX, "core:mana_max");
        TYPE_TO_ID.put(StatType.MANA_RESOURCE, "core:mana");
        TYPE_TO_ID.put(StatType.MANA_REGEN, "core:mana_regen");
    }

    /**
     * Initialize StatRegistry with all StatType enum values.
     * Call once at startup before any game logic runs.
     */
    public static void initializeStatTypes() {
        StatRegistry registry = StatRegistry.getInstance();

        // Combat Stats
        register(registry, StatType.ATTACK_DAMAGE);
        register(registry, StatType.ABILITY_POWER);
        register(registry, StatType.ARMOR);
        register(registry, StatType.MAGIC_RESIST);

        // Speed and Combat Mechanics
        register(registry, StatType.ATTACK_SPEED);
        register(registry, StatType.MOVE_SPEED);
        register(registry, StatType.ABILITY_HASTE);

        // Penetration Stats
        register(registry, StatType.LETHALITY);
        register(registry, StatType.ARMOR_PENETRATION);

        // Critical and Support
        register(registry, StatType.CRIT_CHANCE);
        register(registry, StatType.PROJECTILE_DAMAGE);
        register(registry, StatType.HEAL_AND_SHIELD_POWER);

        // Health Stats
        register(registry, StatType.HEALTH_MAX);
        register(registry, StatType.HEALTH_RESOURCE);
        register(registry, StatType.HEALTH_REGEN);

        // Mana Stats
        register(registry, StatType.MANA_MAX);
        register(registry, StatType.MANA_RESOURCE);
        register(registry, StatType.MANA_REGEN);
    }

    private static void register(StatRegistry registry, StatType type) {
        String id = TYPE_TO_ID.get(type);
        if (id == null) {
            throw new IllegalArgumentException("StatType " + type + " not mapped to id");
        }

        StatDescriptor descriptor = new StatDescriptor(
            id,
            type.getDisplayName(),
            type.getSymbol(),
            type.getColor(),
            mapCategory(type),
            (value, showPlus) -> type.formatValue(value, showPlus)
        );

        registry.register(descriptor);
    }

    /**
     * Map StatType to category (resource vs attribute).
     */
    private static StatDescriptor.StatCategory mapCategory(StatType type) {
        if (type == StatType.HEALTH_RESOURCE || type == StatType.HEALTH_MAX
                || type == StatType.MANA_RESOURCE || type == StatType.MANA_MAX
                || type == StatType.HEALTH_REGEN || type == StatType.MANA_REGEN) {
            return StatDescriptor.StatCategory.RESOURCE;
        }
        return StatDescriptor.StatCategory.ATTRIBUTE;
    }

    /**
     * Convert StatType enum to registry id.
     */
    public static String toId(StatType type) {
        String id = TYPE_TO_ID.get(type);
        if (id == null) {
            throw new IllegalArgumentException("Unknown StatType: " + type);
        }
        return id;
    }

    /**
     * Reverse lookup: id string to StatType enum (if exists).
     */
    public static StatType toStatType(String id) {
        for (Map.Entry<StatType, String> entry : TYPE_TO_ID.entrySet()) {
            if (entry.getValue().equals(id)) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("Unknown id: " + id);
    }
}
