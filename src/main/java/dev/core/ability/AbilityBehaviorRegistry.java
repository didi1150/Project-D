package dev.core.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry mirroring {@link AbilityRegistry}: ability id (or set-passive id)
 * -> behavior factory. Core code invokes factories through the
 * {@link AbilityBehavior} interface; Bukkit implementations are registered
 * during {@code DMain.onEnable}.
 */
public class AbilityBehaviorRegistry {

    private static final Map<String, AbilityBehaviorFactory> FACTORIES = new HashMap<>();

    public static void register(String id, AbilityBehaviorFactory factory) {
        if (id == null || factory == null) return;
        FACTORIES.put(id, factory);
    }

    public static Optional<AbilityBehaviorFactory> getFactory(String id) {
        return Optional.ofNullable(FACTORIES.get(id));
    }

    public static AbilityBehavior create(String id, ActiveAbility ctx) {
        AbilityBehaviorFactory factory = FACTORIES.get(id);
        if (factory == null) return null;
        return factory.create(ctx);
    }

    public static void clear() {
        FACTORIES.clear();
    }

    public static Map<String, AbilityBehaviorFactory> all() {
        return new HashMap<>(FACTORIES);
    }
}
