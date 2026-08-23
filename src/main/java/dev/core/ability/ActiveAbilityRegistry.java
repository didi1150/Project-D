package dev.core.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.core.entity.RPGEntity;
import dev.bukkit.ability.BukkitEffectManager;

/**
 * Central tracking of all active per-holder ability bindings.
 * Replaces the scattered {@code Map<UUID, ...>} state that used to live in the
 * per-item managers with a single surface that answers
 * "which entities currently have ability X" and "which abilities does entity Y have".
 *
 * <p>Keyed by {@link RPGEntity} instance (same convention as
 * {@link BukkitEffectManager}), with ability id as inner key.
 * Backed by {@link ConcurrentHashMap} for safe tick/bus access.</p>
 */
public class ActiveAbilityRegistry {

    private static final ActiveAbilityRegistry INSTANCE = new ActiveAbilityRegistry();

    public static ActiveAbilityRegistry getInstance() {
        return INSTANCE;
    }

    // holder -> (abilityId -> ActiveAbility)
    private final Map<RPGEntity, Map<String, ActiveAbility>> byHolder = new ConcurrentHashMap<>();

    private ActiveAbilityRegistry() {}

    public void track(RPGEntity holder, ActiveAbility active) {
        byHolder.computeIfAbsent(holder, k -> new ConcurrentHashMap<>())
                .put(active.getAbilityId(), active);
    }

    public ActiveAbility untrack(RPGEntity holder, String abilityId) {
        Map<String, ActiveAbility> inner = byHolder.get(holder);
        if (inner == null) return null;
        ActiveAbility removed = inner.remove(abilityId);
        if (inner.isEmpty()) {
            byHolder.remove(holder);
        }
        return removed;
    }

    public void untrackAll(RPGEntity holder) {
        Map<String, ActiveAbility> removed = byHolder.remove(holder);
        if (removed != null) {
            for (ActiveAbility aa : removed.values()) {
                try {
                    if (aa.getBehavior() != null) aa.getBehavior().onDeactivate(aa);
                    aa.getAbility().onDeactivate(aa);
                } catch (Exception ignored) {}
                aa.getSubscriptions().unsubscribeAll();
            }
        }
    }

    public boolean has(RPGEntity holder, String abilityId) {
        Map<String, ActiveAbility> inner = byHolder.get(holder);
        return inner != null && inner.containsKey(abilityId);
    }

    public Optional<ActiveAbility> get(RPGEntity holder, String abilityId) {
        Map<String, ActiveAbility> inner = byHolder.get(holder);
        if (inner == null) return Optional.empty();
        return Optional.ofNullable(inner.get(abilityId));
    }

    public Collection<ActiveAbility> allFor(RPGEntity holder) {
        Map<String, ActiveAbility> inner = byHolder.get(holder);
        if (inner == null) return Collections.emptyList();
        return new ArrayList<>(inner.values());
    }

    public Map<String, ActiveAbility> allForAsMap(RPGEntity holder) {
        Map<String, ActiveAbility> inner = byHolder.get(holder);
        if (inner == null) return Collections.emptyMap();
        return new HashMap<>(inner);
    }

    /**
     * All holders that currently have the given ability/passive active.
     */
    public Set<RPGEntity> holdersOf(String abilityId) {
        Set<RPGEntity> result = new HashSet<>();
        for (Map.Entry<RPGEntity, Map<String, ActiveAbility>> e : byHolder.entrySet()) {
            if (e.getValue().containsKey(abilityId)) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    public List<ActiveAbility> allWithId(String abilityId) {
        List<ActiveAbility> result = new ArrayList<>();
        for (Map<String, ActiveAbility> inner : byHolder.values()) {
            ActiveAbility aa = inner.get(abilityId);
            if (aa != null) result.add(aa);
        }
        return result;
    }

    public Set<String> abilityIdsFor(RPGEntity holder) {
        Map<String, ActiveAbility> inner = byHolder.get(holder);
        if (inner == null) return Collections.emptySet();
        return new HashSet<>(inner.keySet());
    }

    public void clear() {
        // best-effort deactivate before clearing (tests)
        for (RPGEntity holder : new ArrayList<>(byHolder.keySet())) {
            untrackAll(holder);
        }
        byHolder.clear();
    }

    /** Snapshot for diagnostics. */
    public Map<RPGEntity, Map<String, ActiveAbility>> snapshot() {
        Map<RPGEntity, Map<String, ActiveAbility>> copy = new HashMap<>();
        for (Map.Entry<RPGEntity, Map<String, ActiveAbility>> e : byHolder.entrySet()) {
            copy.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return copy;
    }
}
