package dev.bukkit.ability;

import dev.core.ability.Ability;
import dev.core.ability.CooldownSink;
import dev.core.entity.RPGEntity;

/**
 * Cooldown handle bound to a single cast. Caches the cooldown key computed at
 * cast time, so the ability's cooldown cannot drift when the caster swaps
 * items while an effect (e.g. the bonemerang in flight) is still active.
 */
public class BukkitCooldownSink implements CooldownSink {

	private final BukkitEffectManager manager;
	private final RPGEntity entity;
	private final Ability ability;
	private final String key;

	public BukkitCooldownSink(BukkitEffectManager manager, RPGEntity entity, Ability ability, String key) {
		this.manager = manager;
		this.entity = entity;
		this.ability = ability;
		this.key = key;
	}

	@Override
	public void startCooldown() {
		manager.setCooldown(entity, ability, key);
	}

	@Override
	public void startCooldown(long millis) {
		manager.setCooldown(entity, ability, key, millis);
	}

	@Override
	public void clearCooldown() {
		manager.clearCooldown(entity, key);
	}

	@Override
	public long remainingCooldown() {
		return manager.remainingCooldown(entity, key);
	}

}
