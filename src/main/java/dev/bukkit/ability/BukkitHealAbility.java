package dev.bukkit.ability;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/**
 * Nonpractical class, just for showcase purposes at the moment
 * */
public class BukkitHealAbility extends Effect {

	public BukkitHealAbility() {
		// TODO Auto-generated constructor stub
	}

	public void displayParticles(RPGEntity caster) {
		if (caster instanceof BukkitPlayerEntity entity) {
//			entity.getPlayer().stuff();
		}
	}

	@Override
	public void cast(RPGEntity caster, Runnable startCooldown) {
		displayParticles(caster);
	}

	@Override
	public void cancel() {

	}

}
