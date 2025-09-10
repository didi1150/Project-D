package dev.bukkit.event.bukkitListeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import dev.bukkit.event.BukkitEventBus;

public class EventListener implements Listener {

	// TODO add all the necessary events here

	private Plugin plugin;

	public EventListener(Plugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
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

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerHandsSwap(PlayerSwapHandItemsEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onSwap(PlayerItemHeldEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onDamaged(EntityDamageEvent event) {
		BukkitEventBus.getInstance().sendEvent(event);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onDamagedByEntity(EntityDamageByEntityEvent event) {
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

	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		event.getEntity().setCustomName(null);
		event.getEntity().setCustomNameVisible(false);

		event.getEntity().removeMetadata("VANILLA_META", plugin);
	}
}
