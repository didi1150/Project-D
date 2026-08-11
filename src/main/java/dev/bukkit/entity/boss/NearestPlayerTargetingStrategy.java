package dev.bukkit.entity.boss;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;
import dev.core.entity.boss.TargetingStrategy;

public class NearestPlayerTargetingStrategy implements TargetingStrategy {

    @Override
    public Optional<RPGEntity> selectTarget(RPGMobEntity mob) {
        if (!(mob instanceof BukkitBossEntity bukkitBoss)) {
            return Optional.empty();
        }
        Location origin = bukkitBoss.getLivingEntity().map(org.bukkit.entity.Entity::getLocation).orElse(null);
        if (origin == null) {
            return Optional.empty();
        }
        return EntityManager.getInstance().getAliveEntities().stream()
                .filter(entity -> entity != mob && entity.getEntityType() == EntityType.PLAYER && entity.isAlive())
                .filter(entity -> entity instanceof BukkitPlayerEntity)
                .map(entity -> (BukkitPlayerEntity) entity)
                .map(player -> Map.entry(player, player.getPlayer()
                        .map(target -> target.getLocation().distanceSquared(origin)).orElse(Double.MAX_VALUE)))
                .min(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey);
    }
}
