package dev.bukkit.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class EventListener implements Listener {

    //TODO add all the necessary events here

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

}
