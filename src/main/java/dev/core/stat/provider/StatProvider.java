package dev.core.stat.provider;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import dev.core.entity.RPGEntity;
import dev.core.stat.modifier.StatModifier;

/**
 * A provider produces stat modifiers for an entity. Examples: ItemStatProvider,
 * BuffProvider, RoleProvider.
 * 
 * Used to decouple equipment/buffs from direct modifier manipulation.
 */
public interface StatProvider {

    /**
     * Return a mapping from statId -> list of modifiers contributed by this
     * provider. Invoked by StatEngine to aggregate all modifiers.
     *
     * @param entity the entity holding this provider (for context)
     * @return map of statId -> list of modifiers
     */
    @NotNull
    Map<String, List<StatModifier>> provideModifiers(@NotNull RPGEntity entity);

    /**
     * Unique identifier for this provider (used for registration/removal).
     */
    @NotNull
    String getId();

    /**
     * Optional: called when provider is registered with an entity.
     */
    default void onAttach(@NotNull RPGEntity entity) {
    }

    /**
     * Optional: called when provider is unregistered from an entity.
     */
    default void onDetach(@NotNull RPGEntity entity) {
    }

    /**
     * Optional: return true if this provider should be recomputed every frame.
     * Default false (computed once, cached until invalidated).
     */
    default boolean isDynamic() {
        return false;
    }
}
