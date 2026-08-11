package dev.bukkit.entity.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import dev.core.entity.boss.BossEntityContext;

public class BukkitBossEntityContext implements BossEntityContext {

    private static final Random RANDOM = new Random();

    private final Supplier<Location> bossLocation;

    public BukkitBossEntityContext(Supplier<Location> bossLocation) {
        this.bossLocation = bossLocation;
    }

    @Override
    public void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    @Override
    public List<UUID> spawnAdd(String entityType, int count) {
        Location origin = bossLocation.get();
        if (origin == null || origin.getWorld() == null) {
            return List.of();
        }
        EntityType type;
        try {
            type = EntityType.valueOf(entityType.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        List<UUID> spawned = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Location loc = origin.clone().add(RANDOM.nextInt(5) - 2, 0.5, RANDOM.nextInt(5) - 2);
            spawned.add(origin.getWorld().spawnEntity(loc, type).getUniqueId());
        }
        return spawned;
    }
}
