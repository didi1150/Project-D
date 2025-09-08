package dev.bukkit.event.bukkitListeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.Plugin;

import dev.bukkit.utils.DamageUtils;

public class CancelledListener implements Listener {

    private Plugin plugin;

    public CancelledListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            event.setCancelled(true);
        }

        if (event.getEntity() instanceof LivingEntity living) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> DamageUtils.updateName(living), 1L);
        }
    }

    @EventHandler
    public void onChangeFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity le) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DamageUtils.updateName(le);
            }, 1L);
        }
    }

    @EventHandler
    public void onEntityDrop(EntityDropItemEvent event) {
        event.getItemDrop().remove();
        event.setCancelled(true);
    }
}
