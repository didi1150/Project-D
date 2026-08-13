package dev.core.condition;

import java.util.HashMap;
import java.util.Map;

import dev.core.storage.config.ConfigSection;
import dev.core.storage.config.ConfigValidationException;

public final class ConditionRegistry {
    private final Map<String, ConditionFactory> factories = new HashMap<>();

    public void register(String type, ConditionFactory factory) {
        factories.put(type.toUpperCase(), factory);
    }

    public Condition build(ConfigSection params) throws ConfigValidationException {
        String type = params.getString("type", null);
        if (type == null) {
            throw new ConfigValidationException("Condition block is missing 'type': " + params);
        }
        ConditionFactory factory = factories.get(type.toUpperCase());
        if (factory == null) {
            throw new ConfigValidationException("Unknown condition type: " + type);
        }
        return factory.create(params, this);
    }
}
