package dev.bukkit.event.bukkitListeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public class CancelledListener implements Listener {

	@EventHandler
	public void onSpawnEntity(CreatureSpawnEvent event) {
		if (event.getSpawnReason() == SpawnReason.NATURAL) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onRegen(EntityRegainHealthEvent event) {
		event.setCancelled(true);
	}

	@EventHandler
	public void onChangeFood(FoodLevelChangeEvent event) {
		event.setCancelled(true);
	}

}
