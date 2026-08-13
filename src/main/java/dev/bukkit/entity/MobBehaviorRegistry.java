package dev.bukkit.entity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link MobBehavior}s, keyed by the {@code behavior} id used in
 * {@code dungeon-mobs.yml}. Built-in miniboss behaviors are registered lazily on
 * first access; plugins can register their own via {@link #register}.
 */
public final class MobBehaviorRegistry {

    private static MobBehaviorRegistry instance;

    private final Map<String, MobBehavior> behaviors = new ConcurrentHashMap<>();

    private MobBehaviorRegistry() {
        registerDefaults();
    }

    public static MobBehaviorRegistry getInstance() {
        if (instance == null) {
            instance = new MobBehaviorRegistry();
        }
        return instance;
    }

    /** Registers the built-in behaviors shipped with the plugin. */
    private void registerDefaults() {
        register("lost-adventurer", new LostAdventurerBehavior());
        // Register additional built-in miniboss behaviors here.
    }

    /** Register (or replace) a behavior under an id referenced by mob configs. */
    public void register(String id, MobBehavior behavior) {
        if (id != null && !id.isBlank() && behavior != null) {
            behaviors.put(id, behavior);
        }
    }

    /** The behavior for an id, or empty if none is configured/registered. */
    public Optional<MobBehavior> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(behaviors.get(id));
    }

    /** Clears all behaviors and re-registers the built-in defaults. */
    public void clear() {
        behaviors.clear();
        registerDefaults();
    }
}
