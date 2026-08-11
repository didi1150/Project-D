package dev.bukkit.entity.boss;

import java.util.Optional;

import dev.core.entity.boss.AttackPattern;
import dev.core.entity.boss.BossStrategyRegistry;
import dev.core.entity.boss.FirstAlivePlayerTargetingStrategy;
import dev.core.entity.boss.MeleeAttackPattern;
import dev.core.entity.boss.MovementStrategy;
import dev.core.entity.boss.NoopMovementStrategy;
import dev.core.entity.boss.TargetingStrategy;
import dev.core.storage.config.ConfigSection;

public class BukkitBossStrategyRegistry implements BossStrategyRegistry {

    @Override
    public Optional<MovementStrategy> movement(String key, ConfigSection params) {
        switch (key.toUpperCase()) {
        case "NONE":
            return Optional.of(new NoopMovementStrategy());
        case "PATHFIND":
            return Optional.of(new BukkitPathfindingMovementStrategy(params));
        case "TELEPORT":
            return Optional.of(new TeleportMovementStrategy(params));
        default:
            return Optional.empty();
        }
    }

    @Override
    public Optional<TargetingStrategy> targeting(String key, ConfigSection params) {
        switch (key.toUpperCase()) {
        case "NONE":
            return Optional.of(mob -> Optional.empty());
        case "FIRST_ALIVE":
            return Optional.of(new FirstAlivePlayerTargetingStrategy());
        case "NEAREST":
            return Optional.of(new NearestPlayerTargetingStrategy());
        default:
            return Optional.empty();
        }
    }

    @Override
    public Optional<AttackPattern> attack(String key, ConfigSection params) {
        switch (key.toUpperCase()) {
        case "NONE":
            return Optional.of((mob, target, now) -> {
            });
        case "MELEE":
            return Optional.of(new MeleeAttackPattern());
        case "WITHER_SKULL":
            return Optional.of(new WitherSkullAttackPattern(params));
        default:
            return Optional.empty();
        }
    }
}
