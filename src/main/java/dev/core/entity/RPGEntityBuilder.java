package dev.core.entity;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import dev.core.ability.EffectManagerInterface;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventBusInterface;
import dev.core.stat.DefaultStats;
import dev.core.stat.StatManager;
import dev.core.status.NoopStatusEffectManager;
import dev.core.status.StatusEffectManagerInterface;

/**
 * Builder for creating RPGEntity instances with flexible composition.
 * Provides fluent API to configure entities without multiple constructor overloads.
 * 
 * Example usage:
 * <pre>
 * RPGEntity entity = new RPGEntityBuilder(uuid, "Player Name", EntityType.PLAYER)
 *     .withClassType(RPGClassType.WARRIOR)
 *     .withStatManager(customStatManager)
 *     .withEffectManager(effectManager)
 *     .withEventBus(eventBus)
 *     .build();
 * </pre>
 */
public class RPGEntityBuilder {

    private final UUID uuid;
    private final String name;
    private final EntityType entityType;
    
    private StatManager statManager;
    private RPGClassType classType = RPGClassType.TANK;
    private EffectManagerInterface effectManager;
    private EventBusInterface eventBus;
    private StatusEffectManagerInterface statusEffects = NoopStatusEffectManager.getInstance();

    /**
     * Create a new builder with required parameters.
     */
    public RPGEntityBuilder(@NotNull UUID uuid, @NotNull String name, @NotNull EntityType entityType) {
        this.uuid = uuid;
        this.name = name;
        this.entityType = entityType;
    }

    /**
     * Set the stat manager for this entity.
     * If not set, defaults to TANK stats.
     */
    public RPGEntityBuilder withStatManager(@NotNull StatManager statManager) {
        this.statManager = statManager;
        return this;
    }

    /**
     * Set the RPG class type for this entity.
     * Defaults to TANK if not set.
     */
    public RPGEntityBuilder withClassType(@NotNull RPGClassType classType) {
        this.classType = classType;
        return this;
    }

    /**
     * Set the effect manager for this entity.
     * This is required to build.
     */
    public RPGEntityBuilder withEffectManager(@NotNull EffectManagerInterface effectManager) {
        this.effectManager = effectManager;
        return this;
    }

    /**
     * Set the status effect manager for this entity.
     * Defaults to a no-op manager if not set.
     */
    public RPGEntityBuilder withStatusEffectManager(@NotNull StatusEffectManagerInterface statusEffects) {
        this.statusEffects = statusEffects;
        return this;
    }

    /**
     * Set the event bus for this entity.
     * This is required to build.
     */
    public RPGEntityBuilder withEventBus(@NotNull EventBusInterface eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    /**
     * Initialize stat manager with default stats for the given class type.
     */
    public RPGEntityBuilder withDefaultStats(@NotNull RPGClassType classType) {
        this.classType = classType;
        this.statManager = new StatManager(DefaultStats.getStatsByClass(classType));
        return this;
    }

    /**
     * Build the RPGEntity instance.
     * Requires effectManager and eventBus to be set.
     */
    public RPGEntity build() {
        if (effectManager == null) {
            throw new IllegalStateException("effectManager is required");
        }
        if (eventBus == null) {
            throw new IllegalStateException("eventBus is required");
        }

        // Use default TANK stats if no stat manager provided
        if (statManager == null) {
            statManager = new StatManager(DefaultStats.getStatsByClass(RPGClassType.TANK));
        }

        return new RPGEntity(statManager, uuid, name, entityType, effectManager, eventBus, classType, statusEffects) {
        };
    }
}
