package dev.bukkit.game.boss.pillar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class DebugPillarService {

    private static DebugPillarService INSTANCE;

    public static DebugPillarService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DebugPillarService();
        }
        return INSTANCE;
    }

    private final Map<UUID, PillarManager> managers = new ConcurrentHashMap<>();

    private DebugPillarService() {
    }

    public void register(PillarManager manager) {
        managers.put(UUID.randomUUID(), manager);
    }

    public void unregister(PillarManager manager) {
        managers.values().removeIf(value -> value == manager);
    }

    public Collection<PillarManager> getManagers() {
        return List.copyOf(managers.values());
    }

    public PillarManager find(Player player) {
        PillarManager nearest = null;
        double best = Double.MAX_VALUE;
        for (PillarManager manager : managers.values()) {
            double distance = manager.distanceSquared(player.getLocation());
            if (distance < best) {
                best = distance;
                nearest = manager;
            }
        }
        return nearest;
    }

    public LivingEntity findNearestPillarCore(Location location) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (PillarManager manager : managers.values()) {
            LivingEntity core = manager.findNearestPillarCore(location);
            if (core == null || !core.isValid()) {
                continue;
            }
            double distance = core.getLocation().distanceSquared(location);
            if (distance < best) {
                best = distance;
                nearest = core;
            }
        }
        return nearest;
    }

    public PillarManager spawnAt(Player player, int count) {
        PillarConfig config = PillarConfig.defaults();
        int total = Math.max(1, Math.min(8, count));
        World world = player.getWorld();
        Location center = player.getLocation();
        List<Location> bases = new ArrayList<>();
        List<Location> chains = new ArrayList<>();
        double radius = Math.max(5.0, total * 1.5);
        for (int i = 0; i < total; i++) {
            double angle = (Math.PI * 2.0 * i) / total;
            Location base = new Location(world,
                    Math.floor(center.getX() + Math.cos(angle) * radius),
                    Math.floor(center.getY()),
                    Math.floor(center.getZ() + Math.sin(angle) * radius));
            bases.add(base);
            chains.add(base.clone().add(0, config.height() + 5, 0));
        }
        PillarManager manager = new PillarManager(world, center, config, bases, chains, List.of(), true);
        if (!manager.start()) {
            return null;
        }
        return manager;
    }

    public int removeAt(Player player) {
        PillarManager nearest = null;
        double best = Double.MAX_VALUE;
        for (PillarManager manager : managers.values()) {
            if (!manager.isDebugManager()) {
                continue;
            }
            double distance = manager.distanceSquared(player.getLocation());
            if (distance < best) {
                best = distance;
                nearest = manager;
            }
        }
        if (nearest == null) {
            return 0;
        }
        nearest.shutdown(true);
        return 1;
    }

    public int removeAll() {
        int count = 0;
        for (PillarManager manager : List.copyOf(managers.values())) {
            if (manager.isDebugManager()) {
                manager.shutdown(true);
                count++;
            }
        }
        return count;
    }

    public void shutdownAll() {
        for (PillarManager manager : List.copyOf(managers.values())) {
            manager.shutdown(manager.isDebugManager());
        }
    }
}
