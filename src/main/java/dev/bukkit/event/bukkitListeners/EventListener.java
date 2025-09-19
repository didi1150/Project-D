package dev.bukkit.event.bukkitListeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryDragEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onSpawn(PlayerSpawnLocationEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onMove(PlayerToggleSneakEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        event.getEntity().setCustomName(null);
        event.getEntity().setCustomNameVisible(false);

        event.getEntity().removeMetadata("VANILLA_META", plugin);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        BukkitEventBus.getInstance().sendEvent(event);
    }
}
