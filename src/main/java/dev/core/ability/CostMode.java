package dev.core.ability;

import dev.core.stat.StatType;

/**
 * The resource an ability costs. New modes can be added by extending the
 * {@link StatType} mapping; legacy callers addressing resources by their stat
 * name (e.g. "MANA_RESOURCE") keep working via {@link #fromResourceType}.
 */
public enum CostMode {

    MANA,
    HEALTH;

    /**
     * The {@link StatType} name of the resource this mode consumes
     * (e.g. {@code MANA_RESOURCE}), matching the stat keys used by the stat
     * manager and mana discount logic.
     */
    public String getResourceType() {
        return switch (this) {
        case MANA -> StatType.MANA_RESOURCE.name();
        case HEALTH -> StatType.HEALTH_RESOURCE.name();
        };
    }

    /**
     * Maps legacy resource-type strings ("MANA_RESOURCE", "HEALTH_RESOURCE",
     * and any variant containing those words) to a {@link CostMode}. Throws for
     * unknown resources.
     */
    public static CostMode fromResourceType(String resourceType) {
        if (resourceType == null) {
            throw new IllegalArgumentException("Cost mode requires a resource type");
        }
        String value = resourceType.toUpperCase();
        if (value.contains("MANA")) {
            return MANA;
        }
        if (value.contains("HEALTH")) {
            return HEALTH;
        }
        throw new IllegalArgumentException("Unknown cost resource: " + resourceType);
    }
}