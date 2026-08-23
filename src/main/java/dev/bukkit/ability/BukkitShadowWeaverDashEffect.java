package dev.bukkit.ability;

import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.bukkit.ability.behavior.ShadowWeaverBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.ability.ActiveAbilityRegistry;
import dev.core.ability.impl.ShadowWeaverStaffAbility;

/**
 * Backs the {@code SHADOW_STAFF_DASH} ability: a left-click while locked onto a
 * highlighted platform dashes the assassin to it. All of the dash animation and
 * sticky-lock logic lives in {@link ShadowWeaverBehavior}; this effect is the
 * thin adapter between the ability pipeline and the per-holder behavior state.
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
		ActiveAbility aa = ActiveAbilityRegistry.getInstance()
				.get(caster, ShadowWeaverStaffAbility.DASH_ID).orElse(null);
		if (aa != null && aa.getBehavior() instanceof ShadowWeaverBehavior beh) {
			beh.handleDash(player);
		}
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
