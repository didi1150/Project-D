package dev.bukkit.entity.boss;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.bukkit.utils.StealthRegistry;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.boss.BossStrategyRegistry;
import dev.core.entity.strategy.AttackPattern;
import dev.core.entity.strategy.MeleeAttackPattern;
import dev.core.entity.strategy.MovementStrategy;
import dev.core.entity.strategy.NoopMovementStrategy;
import dev.core.entity.strategy.TargetingStrategy;
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
            return Optional.of(firstAlivePlayerTarget());
        case "NEAREST":
            return Optional.of(new NearestPlayerTargetingStrategy());
        default:
            return Optional.empty();
        }
    }

    static TargetingStrategy firstAlivePlayerTarget() {
        return mob -> EntityManager.getInstance().getAliveEntities().stream()
                .filter(entity -> entity != mob && entity.getEntityType() == EntityType.PLAYER && entity.isAlive())
                .filter(entity -> {
                    Player player = Bukkit.getPlayer(entity.getUuid());
                    return player == null || !StealthRegistry.shouldHideFromMob(player);
                })
                .findFirst();
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
