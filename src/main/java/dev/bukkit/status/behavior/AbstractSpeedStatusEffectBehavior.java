package dev.bukkit.status.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.stat.BukkitStatManager;
import dev.bukkit.status.StatusEffectBehavior;
import dev.bukkit.status.StatusEffectContext;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;

/**
 * Shared movement-restriction CC behavior (slowed / rooted).
 *
 * <p>
 * RPG entities drive their movement from the {@code MOVE_SPEED} stat: a
 * multiplicative {@link StatModifier} is pushed into the stat engine and the
 * vanilla {@code MOVEMENT_SPEED} attribute is re-synced from the slowed value
 * (players re-sync every tick anyway via {@link BukkitStatManager}). Entities
 * without a {@code MOVE_SPEED} stat (vanilla-only mobs) fall back to a
 * hidden-particle SLOWNESS potion, which expires on its own.
 */
public abstract class AbstractSpeedStatusEffectBehavior implements StatusEffectBehavior {

	private final Map<UUID, StatModifier> activeModifiers = new HashMap<>();

	/**
	 * Multiplier applied to the move-speed stat while active (e.g. 0.6 = 40%
	 * slower).
	 */
	protected abstract double moveSpeedFactor();

	/** SLOWNESS amplifier used on the potion fallback path. */
	protected abstract int slownessAmplifier();

	/** Unique modifier source id (per type, so slow and root never collide). */
	protected abstract String sourceId();

	@Override
	public void onApply(StatusEffectContext ctx) {
		LivingEntity living = ctx.getLivingEntity();
		RPGEntity rpg = ctx.getRpgEntity();
		if (rpg.getStatManager().getStats().containsKey(StatType.MOVE_SPEED)) {
			StatModifier modifier = StatModifier.builder(moveSpeedFactor(), StatModifierType.MULTIPLY,
					StatType.MOVE_SPEED, sourceId()).build();
			rpg.addStatModifier(modifier);
			activeModifiers.put(rpg.getUuid(), modifier);
			syncSpeed(living, rpg);
		} else if (Bukkit.getServer() != null) {
			// Vanilla-only entity: self-expiring potion. Guarded so headless
			// contexts (tests) never touch the Registry-backed potion types.
			long ticks = Math.max(1, ctx.getEffect().remaining(System.currentTimeMillis()) / 50L);
			living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) ticks, slownessAmplifier(),
					false, false));
		}
	}

	@Override
	public void onTick(StatusEffectContext ctx, long now) {
		// The stat engine / potion carries the CC; nothing to do per tick.
	}

	@Override
	public void onEnd(StatusEffectContext ctx) {
		RPGEntity rpg = ctx.getRpgEntity();
		LivingEntity living = ctx.getLivingEntity();
		StatModifier modifier = activeModifiers.remove(rpg.getUuid());
		if (modifier != null) {
			rpg.removeStatModifier(modifier);
			syncSpeed(living, rpg);
		} else if (Bukkit.getServer() != null) {
			living.removePotionEffect(PotionEffectType.SLOWNESS);
		}
	}

	private void syncSpeed(LivingEntity living, RPGEntity rpg) {
		if (Bukkit.getServer() == null) {
			return; // attribute sync needs a live server (headless tests skip it)
		}
		AttributeInstance attribute = living.getAttribute(Attribute.MOVEMENT_SPEED);
		if (attribute == null) {
			return;
		}
		double value = rpg.getStatEngineAdapter().getCurrentValue(StatType.MOVE_SPEED, System.currentTimeMillis());
		attribute.setBaseValue(BukkitStatManager.computeMoveSpeed(value));
	}
}