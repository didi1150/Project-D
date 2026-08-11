package dev.core.entity.boss;

import java.util.Optional;

import dev.core.storage.config.ConfigSection;

/**
 * Resolves strategy keys from boss stage configuration into strategy
 * implementations. The stage's {@link ConfigSection} is passed along so
 * strategies can read their own parameters.
 */
public interface BossStrategyRegistry {

    Optional<MovementStrategy> movement(String key, ConfigSection params);

    Optional<TargetingStrategy> targeting(String key, ConfigSection params);

    Optional<AttackPattern> attack(String key, ConfigSection params);
}
