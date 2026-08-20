package dev.bukkit.status.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import dev.bukkit.status.StatusEffectBehavior;
import dev.bukkit.status.StatusEffectContext;

/**
 * Hard CC: launches the target into the air and keeps it floating for the
 * duration (gravity off, horizontal drift cancelled). Casting and attacking
 * are blocked by the core manager's hard-CC guard. Gravity is restored on end
 * so the target falls back down.
 */
public final class AirborneStatusEffectBehavior implements StatusEffectBehavior {

	private static final double LAUNCH_UPWARD_SPEED = 1.0;

	private final Map<UUID, Boolean> previousGravity = new HashMap<>();

	@Override
	public void onApply(StatusEffectContext ctx) {
		LivingEntity living = ctx.getLivingEntity();
		previousGravity.put(ctx.getRpgEntity().getUuid(), living.hasGravity());
		living.setGravity(false);
		Vector velocity = living.getVelocity();
		living.setVelocity(new Vector(velocity.getX(), LAUNCH_UPWARD_SPEED, velocity.getZ()));
	}

	@Override
	public void onTick(StatusEffectContext ctx, long now) {
		LivingEntity living = ctx.getLivingEntity();
		// Hold altitude: keep the launch's vertical speed, cancel horizontal
		// drift so the target hangs in place rather than sliding around.
		Vector velocity = living.getVelocity();
		living.setVelocity(new Vector(0, Math.max(0, velocity.getY()), 0));
	}

	@Override
	public void onEnd(StatusEffectContext ctx) {
		LivingEntity living = ctx.getLivingEntity();
		Boolean gravity = previousGravity.remove(ctx.getRpgEntity().getUuid());
		if (gravity != null) {
			living.setGravity(gravity);
		}
	}
}