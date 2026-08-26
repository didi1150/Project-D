package dev.bukkit.entity.boss;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;

/**
 * Utility class for registering display entities and unified removal
 */
public class BukkitDisplayEntityRegistry {

    private Set<UUID> trackedEntities;
    private static BukkitDisplayEntityRegistry INSTANCE;

    private BukkitDisplayEntityRegistry() {
        this.trackedEntities = new HashSet<UUID>();
    }

    public static BukkitDisplayEntityRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BukkitDisplayEntityRegistry();
        }

        return INSTANCE;
    }

    public <T extends Display> T spawnDisplayEntity(Location location, Class<T> type) {
        return spawnDisplayEntity(location, type, d -> {});
    }

    public <T extends Display> T spawnDisplayEntity(Location location, Class<T> type, Consumer<T> configurator) {
        T entity = location.getWorld().spawn(location, type, configurator);
        trackedEntities.add(entity.getUniqueId());
        return entity;
    }

    public void removeDisplayEntity(UUID uuid) {
        if (!trackedEntities.contains(uuid) || Bukkit.getEntity(uuid) == null) {
            return;
        }

        trackedEntities.remove(uuid);
        Bukkit.getEntity(uuid).remove();
    }

    public void untrack(UUID uuid) {
        trackedEntities.remove(uuid);
    }

    public void removeAllDisplays() {
        Iterator<UUID> iterator = trackedEntities.iterator();
        while (iterator.hasNext()) {
            UUID next = iterator.next();
            iterator.remove();
            if (Bukkit.getEntity(next) != null) {
                Bukkit.getEntity(next).remove();
            }
        }
    }
}
