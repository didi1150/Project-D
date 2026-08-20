package dev.bukkit.event.subscribers;

import org.bukkit.event.block.BlockPlaceEvent;

import dev.bukkit.item.BukkitItemStackAdapter;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

@EventSubscriber
public class BlockSubscriber {

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (BukkitItemStackAdapter.getRpgItemId(event.getPlayer().getInventory().getItemInMainHand()) != null) {
            event.setCancelled(true);
        }
    }
}