package dev.bukkit.ability;

import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/**
 * Backs the {@code SHADOW_STAFF_DASH} ability: a left-click while locked onto a
 * highlighted platform dashes the assassin to it. All of the dash animation and
 * sticky-lock logic lives in {@link ShadowWeaverManager}; this effect is the
 * thin adapter between the ability pipeline and the manager's per-player state.
 */
public class BukkitShadowWeaverDashEffect extends Effect {

	public BukkitShadowWeaverDashEffect(String cooldownKey) {
		super(null, 1, false, cooldownKey);
	}

	@Override
	public void cast(RPGEntity caster, CooldownSink cooldownSink) {
		Player player = resolvePlayer(caster);
		if (player == null) {
			return;
		}
		cooldownSink.startCooldown();
		ShadowWeaverManager.getInstance().handleDash(player);
	}

	@Override
	public void cancel() {
		// The effect is an instant fire-and-forget delegate; nothing to tear down.
	}

	private static Player resolvePlayer(RPGEntity caster) {
		if (caster instanceof BukkitPlayerEntity playerEntity) {
			return playerEntity.getPlayer().orElse(null);
		}
		return null;
	}
}