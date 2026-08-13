package dev.bukkit.ability;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/**
 * Nonpractical class, just for showcase purposes at the moment
 */
public class BukkitHealEffect extends Effect {

	public BukkitHealEffect(String cooldownKey) {
		super(null, 1, false, cooldownKey);
	}

	public void displayParticles(RPGEntity caster) {
		if (caster instanceof BukkitPlayerEntity) {
//			entity.getPlayer().stuff();
		}
	}

	@Override
	public void cast(RPGEntity caster, Runnable startCooldown, Runnable resetCooldown) {
		displayParticles(caster);
	}

	@Override
	public void cancel() {

	}

	@Override
	public boolean hasExpired(long now) {
		return true;
	}

}
