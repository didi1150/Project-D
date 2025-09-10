package dev.bukkit.event.bukkitListeners;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;

public class CancelledListener implements Listener {

    private Plugin plugin;

    public CancelledListener(Plugin plugin, ProtocolManager protocolManager) {
        this.plugin = plugin;

        protocolManager.addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.ARM_ANIMATION) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        Player player = event.getPlayer();
                        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                        if (optional.isEmpty()) {
                            return;
                        } else {
                            optional.get().recordSwing();
                            if (!optional.get().canAttack()) {
                                event.setCancelled(true); // cancel arm swing packet
                            }
                        }
                    }
                });
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

    @EventHandler(priority = EventPriority.MONITOR)
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
