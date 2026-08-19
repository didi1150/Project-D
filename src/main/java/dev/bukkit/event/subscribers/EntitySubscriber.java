package dev.bukkit.event.subscribers;

import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;

import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

/**
 * Cleans up the vanilla metadata applied to mobs/spawns on death
 * (see {@code CancelSubscriber.cancelCreatureSpawn}).
 */
@EventSubscriber
public class EntitySubscriber {

    private final Plugin plugin;

    public EntitySubscriber(Plugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onEntityDeath(EntityDeathEvent event) {
        event.getEntity().setCustomName(null);
        event.getEntity().setCustomNameVisible(false);

        event.getEntity().removeMetadata("VANILLA_META", plugin);
    }
}