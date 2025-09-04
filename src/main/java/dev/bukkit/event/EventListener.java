package dev.bukkit.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public class EventListener implements Listener {

	// TODO add all the necessary events here

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
		event.setCancelled(false);
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onSwap(PlayerItemHeldEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onDamaged(EntityDamageEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onClick(InventoryClickEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onSpawn(PlayerSpawnLocationEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

}
