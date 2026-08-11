package dev.bukkit.entity.boss;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;
import dev.core.entity.boss.MovementStrategy;
import dev.core.storage.config.ConfigSection;

public class TeleportMovementStrategy implements MovementStrategy {

    private final double teleportDistanceSquared;
    private final long cooldownMillis;
    private long lastTeleportAt;

    public TeleportMovementStrategy(ConfigSection params) {
        double teleportDistance = params.getDouble("teleport-distance", 15.0);
        this.teleportDistanceSquared = teleportDistance * teleportDistance;
        this.cooldownMillis = params.getInt("teleport-cooldown-ms", 2000);
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
        Optional<LivingEntity> living = bukkitBoss.getLivingEntity();
        if (player.isEmpty() || living.isEmpty()) {
            return;
        }
        if (now - lastTeleportAt < cooldownMillis) {
            return;
        }
        if (living.get().getLocation().distanceSquared(player.get().getLocation()) > teleportDistanceSquared) {
            Location destination = player.get().getLocation().clone().add(0, 1, 0);
            living.get().teleport(destination);
            if (destination.getWorld() != null) {
                destination.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
            lastTeleportAt = now;
        }
    }
}
