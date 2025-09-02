package dev.bukkit.entity;

import org.bukkit.entity.Player;

import dev.bukkit.ability.BukkitEffectManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatManager;

public class BukkitPlayerEntity extends RPGEntity {

	private Player player;

	public BukkitPlayerEntity(StatManager statManager, Player player) {
		super(statManager, player.getUniqueId(), player.getName(), EntityType.PLAYER,
				BukkitEffectManager.getInstance());
	}

	public Player getPlayer() {
		return player;
	}

}
