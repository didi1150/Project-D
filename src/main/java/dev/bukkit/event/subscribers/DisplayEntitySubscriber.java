package dev.bukkit.event.subscribers;

import org.bukkit.entity.Display;
import org.bukkit.event.entity.EntityRemoveEvent;

import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

/**
 * Automatically removes display entities from the tracked set
 * when they are removed from the world for any reason.
 */
@EventSubscriber
public class DisplayEntitySubscriber {

    @Subscribe
    public void onEntityRemoved(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Display) {
            BukkitDisplayEntityRegistry.getInstance().untrack(event.getEntity().getUniqueId());
        }
    }
}
