package dev.bukkit.entity.boss;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;
import dev.core.entity.boss.MovementStrategy;
import dev.core.storage.config.ConfigSection;

/**
 * Steers the boss toward its target using velocity. Works with vanilla AI
 * disabled, so it can be combined with the custom targeting/attack logic.
 */
public class BukkitPathfindingMovementStrategy implements MovementStrategy {

    private final double speed;
    private final double stopDistanceSquared;

    public BukkitPathfindingMovementStrategy(ConfigSection params) {
        this.speed = params.getDouble("movement-speed", 1.0);
        double stopDistance = params.getDouble("stop-distance", 2.0);
        this.stopDistanceSquared = stopDistance * stopDistance;
    }

    @Override
    public void updateMovement(RPGMobEntity mob, Optional<RPGEntity> currentTarget, long now) {
        if (currentTarget.isEmpty() || !(mob instanceof BukkitBossEntity bukkitBoss)) {
            return;
        }
        RPGEntity target = currentTarget.get();
        if (!(target instanceof BukkitPlayerEntity playerEntity)) {
            return;
        }
        Optional<Player> player = playerEntity.getPlayer();
        if (player.isEmpty()) {
            return;
        }
        Optional<LivingEntity> living = bukkitBoss.getLivingEntity();
        if (living.isEmpty()) {
            return;
        }

        LivingEntity entity = living.get();
        Location entityLocation = entity.getLocation();
        if (entityLocation.getWorld() == null) {
            return;
        }

        if (entityLocation.distanceSquared(player.get().getLocation()) <= stopDistanceSquared) {
            Vector currentVelocity = entity.getVelocity();
            entity.setVelocity(new Vector(0, currentVelocity.getY(), 0));
            return;
        }

        Vector direction = player.get().getLocation().toVector().subtract(entityLocation.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1e-4) {
            return;
        }

        Vector velocity = direction.normalize().multiply(speed * 0.25);
        if (entity.isOnGround()
                && !entityLocation.getBlock().getRelative(BlockFace.UP).getType().isAir()) {
            velocity.setY(0.42);
        }
        entity.setVelocity(velocity);
    }
}
