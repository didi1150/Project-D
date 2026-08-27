package dev.bukkit.ability;

import java.util.Optional;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.ability.behavior.SupportStaffBehavior;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/**
 * SHIFT-click effect for the Utility Staff. Cycles the staff's current mode:
 * Mending Touch (1) → Aegis Ward (2) → Tempest Gust (3) → Mending Touch (1).
 * No cooldown, no cost. The mode change is reflected immediately in the HUD
 * overlay and the targeting indicators managed by {@link SupportStaffBehavior}.
 */
public class BukkitSupportStaffToggleEffect extends Effect {

	public BukkitSupportStaffToggleEffect(String cooldownKey) {
		super(null, -1, false, cooldownKey);
	}

	@Override
	public void cast(RPGEntity caster, CooldownSink cooldownSink) {
		if (!(caster instanceof BukkitPlayerEntity playerEntity))
			return;
		Optional<Player> optPlayer = playerEntity.getPlayer();
		if (optPlayer.isEmpty())
			return;
		Player player = optPlayer.get();
		if (player.isDead() || !player.isOnline())
			return;

		SupportStaffBehavior state = SupportStaffBehavior.forHolder(caster.getUuid());
		if (state == null)
			return;

		state.cycleMode();
		player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
	}

	@Override
	public void cancel() {
	}
}
