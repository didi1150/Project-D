package dev.bukkit.ability;

import org.bukkit.entity.Fireball;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

public class BukkitParticleTestEffect extends Effect {

	public BukkitParticleTestEffect() {
		super(null, 1, false);
	}

	@Override
	public void cast(RPGEntity caster, Runnable startCooldown, Runnable resetCooldown) {
		if (caster instanceof BukkitPlayerEntity playerEntity) {
			startCooldown.run();
			playerEntity.getPlayer().playEffect(playerEntity.getPlayer().getLocation(), org.bukkit.Effect.BLAZE_SHOOT,
					null);
			playerEntity.getPlayer().launchProjectile(Fireball.class);
		}
	}

	@Override
	public void cancel() {

	}

}
