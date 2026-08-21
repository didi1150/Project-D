package dev.core.stat.descriptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

/**
 * Central registry for all stat definitions. Supports runtime registration and
 * lookup by id.
 */
public class StatRegistry {

    private static StatRegistry instance;
    private final Map<String, StatDescriptor> stats = new HashMap<>();

    private StatRegistry() {
    }

    public static synchronized StatRegistry getInstance() {
        if (instance == null) {
            instance = new StatRegistry();
        }
        return instance;
    }

    /**
     * Register a stat descriptor.
     */
    public void register(@NotNull StatDescriptor descriptor) {
        if (stats.containsKey(descriptor.getId())) {
            throw new IllegalArgumentException("Stat '" + descriptor.getId() + "' already registered");
        }
        stats.put(descriptor.getId(), descriptor);
    }

    /**
     * Replace an existing stat descriptor (kept for config-driven metadata
     * overrides). Unknown ids are registered like {@link #register}.
     */
    public void override(@NotNull StatDescriptor descriptor) {
        stats.put(descriptor.getId(), descriptor);
    }

    /**
     * Lookup a stat descriptor by id.
     */
    public Optional<StatDescriptor> get(@NotNull String statId) {
        return Optional.ofNullable(stats.get(statId));
    }

    /**
     * Lookup a stat descriptor, throws if not found.
     */
    public StatDescriptor getOrThrow(@NotNull String statId) {
        StatDescriptor desc = stats.get(statId);
        if (desc == null) {
            throw new IllegalArgumentException("Unknown stat: " + statId);
        }
        return desc;
    }

    /**
     * Check if stat is registered.
     */
    public boolean isRegistered(@NotNull String statId) {
        return stats.containsKey(statId);
    }

    /**
     * Get all registered stats.
     */
    public Map<String, StatDescriptor> getAll() {
        return new HashMap<>(stats);
    }

    /**
     * Clear all stats (for testing/reloading).
     */
    public void clear() {
        stats.clear();
    }

    /**
     * Get count of registered stats.
     */
    public int size() {
        return stats.size();
    }
}
