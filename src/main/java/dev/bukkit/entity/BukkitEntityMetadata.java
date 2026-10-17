package dev.bukkit.entity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Replacement for Bukkit's deprecated metadata API.
 *
 * <p>
 * Values are keyed by entity UUID and can hold arbitrary objects (for example
 * {@link VanillaEntityMeta}). Entries must be removed when the entity leaves
 * the world; {@code EntitySubscriber} wires that cleanup to
 * {@code EntityRemoveEvent}.
 */
public final class BukkitEntityMetadata {

    private static final Map<UUID, Map<String, Object>> VALUES = new ConcurrentHashMap<>();

    private BukkitEntityMetadata() {
    }

    public static void set(Entity entity, String key, @Nullable Object value) {
        if (entity == null) {
            return;
        }
        if (value == null) {
            remove(entity, key);
            return;
        }
        VALUES.computeIfAbsent(entity.getUniqueId(), _ -> new ConcurrentHashMap<>()).put(key, value);
    }

    public static boolean has(Entity entity, String key) {
        if (entity == null) {
            return false;
        }
        Map<String, Object> values = VALUES.get(entity.getUniqueId());
        return values != null && values.containsKey(key);
    }

    public static <T> Optional<T> get(Entity entity, String key, Class<T> type) {
        if (entity == null) {
            return Optional.empty();
        }
        Map<String, Object> values = VALUES.get(entity.getUniqueId());
        if (values == null) {
            return Optional.empty();
        }
        Object value = values.get(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    public static void remove(Entity entity, String key) {
        if (entity == null) {
            return;
        }
        Map<String, Object> values = VALUES.get(entity.getUniqueId());
        if (values == null) {
            return;
        }
        values.remove(key);
        if (values.isEmpty()) {
            VALUES.remove(entity.getUniqueId());
        }
    }

    public static void clear(Entity entity) {
        if (entity != null) {
            VALUES.remove(entity.getUniqueId());
        }
    }
}
