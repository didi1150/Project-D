package dev.bukkit.event.bukkitListeners;

import java.util.List;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import dev.bukkit.event.BukkitEventBus;

public class EventBusRegistry {
    private record Reg(Class<? extends Event> type, EventPriority priority, boolean ignoreCancelled) {
    }

    private static final List<Reg> FORWARDED = List.of(new Reg(PlayerJoinEvent.class, EventPriority.MONITOR, false),
            new Reg(PlayerQuitEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerInteractEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerPortalEvent.class, EventPriority.LOW, false),
            new Reg(PlayerSwapHandItemsEvent.class, EventPriority.LOWEST, false),
            new Reg(PlayerChangedWorldEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerDeathEvent.class, EventPriority.NORMAL, false),
            new Reg(BlockBreakEvent.class, EventPriority.NORMAL, false),
            new Reg(BlockPlaceEvent.class, EventPriority.NORMAL, false),
            new Reg(EntityDamageEvent.class, EventPriority.LOW, false),
            new Reg(EntityDamageByEntityEvent.class, EventPriority.LOW, false),
            new Reg(InventoryClickEvent.class, EventPriority.MONITOR, false),
            new Reg(InventoryDragEvent.class, EventPriority.MONITOR, false),
            new Reg(InventoryCloseEvent.class, EventPriority.NORMAL, false),
            new Reg(EntityPickupItemEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerDropItemEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerSpawnLocationEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerMoveEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerToggleSneakEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerCommandPreprocessEvent.class, EventPriority.NORMAL, false),
            new Reg(EntityDeathEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerInteractAtEntityEvent.class, EventPriority.NORMAL, false),
            new Reg(PlayerItemHeldEvent.class, EventPriority.LOWEST, false),
            new Reg(EntityRegainHealthEvent.class, EventPriority.NORMAL, false),
            new Reg(FoodLevelChangeEvent.class, EventPriority.NORMAL, false),
            new Reg(EntityTargetLivingEntityEvent.class, EventPriority.NORMAL, false),
            new Reg(EntityExplodeEvent.class, EventPriority.NORMAL, false),
            new Reg(BlockExplodeEvent.class, EventPriority.NORMAL, false),
            new Reg(LeavesDecayEvent.class, EventPriority.NORMAL, false),
            new Reg(CreatureSpawnEvent.class, EventPriority.NORMAL, false),
            new Reg(PortalCreateEvent.class, EventPriority.NORMAL, false),
            new Reg(EntityPortalEvent.class, EventPriority.NORMAL, false),
            new Reg(EntityShootBowEvent.class, EventPriority.NORMAL, false),
            new Reg(AsyncPlayerChatEvent.class, EventPriority.NORMAL, false));

    public static void registerAll(Plugin plugin) {
        Listener dummy = new Listener() {
        };
        PluginManager pm = plugin.getServer().getPluginManager();

        for (Reg r : FORWARDED) {
            pm.registerEvent(r.type(), dummy, r.priority(), (l, event) -> BukkitEventBus.getInstance().sendEvent(event),
                    plugin, r.ignoreCancelled());
        }
    }
}
