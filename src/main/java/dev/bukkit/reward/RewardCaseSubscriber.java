package dev.bukkit.reward;

import org.bukkit.entity.Interaction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

/**
 * Event-bus subscriber for the reward case GUI and chest interaction.
 * The case service is a singleton initialized before the subscriber scan runs.
 */
@EventSubscriber
public final class RewardCaseSubscriber {

    private final RewardCaseService service = RewardCaseService.getInstance();

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Interaction interaction) {
            if (service.handleChestInteract(event.getPlayer(), interaction)) {
                event.setCancelled(true);
            }
        }
    }

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof Interaction interaction) {
            if (service.handleChestInteract(event.getPlayer(), interaction)) {
                event.setCancelled(true);
            }
        }
    }

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onClick(InventoryClickEvent event) {
        service.handleClick(event);
    }

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onDrag(InventoryDragEvent event) {
        service.handleDrag(event);
    }

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onClose(InventoryCloseEvent event) {
        service.handleClose(event);
    }

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event);
    }
}
