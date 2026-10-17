package dev.bukkit.event.subscribers;

import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

import dev.bukkit.entity.BukkitEntityMetadata;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

/**
 * Cleans up the vanilla metadata applied to mobs/spawns on death (see
 * {@code CancelSubscriber.cancelCreatureSpawn}).
 */
@EventSubscriber
public class EntitySubscriber {
    @Subscribe
    public void onEntityDeath(EntityDeathEvent event) {
        event.getEntity().customName(null);
        event.getEntity().setCustomNameVisible(false);

        BukkitEntityMetadata.remove(event.getEntity(), "VANILLA_META");
    }

    @Subscribe
    public void onEntityRemove(EntityRemoveEvent event) {
        BukkitEntityMetadata.clear(event.getEntity());
    }
}