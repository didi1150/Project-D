package dev.bukkit.reward.special;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

/** Event-bus bridge for the forced Tic Tac Toe games. */
@EventSubscriber
public final class TicTacToeSubscriber {

    private final TicTacToeService service = TicTacToeService.getInstance();

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

    @Subscribe
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }
}
