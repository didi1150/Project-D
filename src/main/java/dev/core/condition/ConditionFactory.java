package dev.core.condition;

import dev.core.storage.config.ConfigSection;
import dev.core.storage.config.ConfigValidationException;

public interface ConditionFactory {
    Condition create(ConfigSection params, ConditionRegistry registry) throws ConfigValidationException;
}
