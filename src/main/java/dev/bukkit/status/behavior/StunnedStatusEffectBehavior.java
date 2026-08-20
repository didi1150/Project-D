package dev.bukkit.status.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import dev.bukkit.status.StatusEffectBehavior;
import dev.bukkit.status.StatusEffectContext;

/**
 * Hard CC: full control loss. Pauses the mob's AI (and thus its vanilla
 * movement and targeting) and pins velocities to zero so knockback and
 * knock-up don't move a stunned target. Casting and attacking are already
 * blocked by the core manager through
 * {@code RPGEntity.triggerAbility}/{@code RPGEntity.canAttack}. The previous
 * aware-state is restored on end.
 */
public final class StunnedStatusEffectBehavior implements StatusEffectBehavior {

	private final Map<UUID, Boolean> previousAware = new HashMap<>();

	@Override
	public void onApply(StatusEffectContext ctx) {
		LivingEntity living = ctx.getLivingEntity();
		living.setVelocity(new Vector(0, 0, 0));
		if (living instanceof Mob mob) {
			previousAware.put(ctx.getRpgEntity().getUuid(), mob.isAware());
			mob.setAware(false);
		}
	}

	@Override
	public void onTick(StatusEffectContext ctx, long now) {
		// Pin the target: no knockback, no residual velocity.
		ctx.getLivingEntity().setVelocity(new Vector(0, 0, 0));
	}

	@Override
	public void onEnd(StatusEffectContext ctx) {
		LivingEntity living = ctx.getLivingEntity();
		if (living instanceof Mob mob) {
			Boolean aware = previousAware.remove(ctx.getRpgEntity().getUuid());
			if (aware != null) {
				mob.setAware(aware);
			}
		}
	}
}