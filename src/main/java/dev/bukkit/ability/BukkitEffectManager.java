package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;

import dev.core.ability.Ability;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.impl.ParticleTestAbility;
import dev.core.ability.impl.SwingBoneAbility;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;

public class BukkitEffectManager implements EffectManagerInterface {

	private Map<RPGEntity, List<Effect>> activeEffects;
	private Map<RPGEntity, Map<String, Long>> cooldowns;

	private static EffectManagerInterface instance;

	private BukkitEffectManager() {
		this.activeEffects = new HashMap<RPGEntity, List<Effect>>();
		this.cooldowns = new HashMap<RPGEntity, Map<String, Long>>();
	}

	public static EffectManagerInterface getInstance() {
		if (instance == null) {
			instance = new BukkitEffectManager();
		}
		return instance;
	}

	@Override
	public Effect cast(RPGEntity entity, Ability ability) {

		if (!canActivate(entity, ability)) {
			return null;
		}
		Effect effect = null;
		for (Entry<String, Double> entry : ability.getCost().getResourceCosts().entrySet()) {
			entity.getStatManager().modifyStat(StatType.valueOf(entry.getKey()), entry.getValue());
		}

		if (ability instanceof ParticleTestAbility) {
			effect = new BukkitParticleTestEffect();
			effect.cast(entity, () -> setCooldown(entity, ability), () -> {
			});
		}

		if (ability instanceof SwingBoneAbility) {
			effect = new BukkitSwingBoneEffect();
			boolean alreadyActive = false;
			if (activeEffects.containsKey(entity)) {
				for (Effect e : activeEffects.get(entity)) {
					if (e instanceof BukkitSwingBoneEffect) {
						alreadyActive = true;
						break;
					}
				}
			}
			if (!alreadyActive) {
				effect.cast(entity, () -> setCooldown(entity, ability),
						() -> reduceCooldown(entity, ability, ability.getCooldown()));
			} else {
				return effect;
			}
		}

		if (effect != null) {
			List<Effect> effectsList = activeEffects.getOrDefault(entity, new ArrayList<Effect>());
			effectsList.add(effect);
			activeEffects.put(entity, effectsList);
		}
		return effect;
	}

	@Override
	public boolean canActivate(RPGEntity entity, Ability ability) {
		if (isOnCooldown(entity, ability)) {
			return false;
		}

		for (Entry<String, Double> entry : ability.getCost().getResourceCosts().entrySet()) {
			if (entity.getStatManager().getCurrentValue(StatType.valueOf(entry.getKey()),
					System.currentTimeMillis()) < entry.getValue()) {
				return false;
			}
		}
		// TODO: Other clauses
		return true;
	}

	public long remainingCooldown(RPGEntity entity, Ability ability) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns == null) {
			return 0; // No cooldowns for this entity
		}

		Long cooldownEnd = entityCooldowns.get(ability.getId());
		if (cooldownEnd == null) {
			return 0; // No cooldown for this specific ability
		}

		long currentTime = System.currentTimeMillis();
		long remaining = Math.max(0, cooldownEnd - currentTime); // Return 0 if cooldown has expired
		if (remaining == 0) {
			// Remove from cooldown list if cooldowns expire
			cooldowns.get(entity).remove(ability.getId());
		}
		return remaining;
	}

	private boolean isOnCooldown(RPGEntity entity, Ability ability) {
		long remainingCooldown = remainingCooldown(entity, ability);
		return remainingCooldown > 0;
	}

	/**
	 * Set the cooldown for an ability after it has been used
	 */
	private void setCooldown(RPGEntity entity, Ability ability) {
		// Get or create cooldown map for this entity
		Map<String, Long> entityCooldowns = cooldowns.computeIfAbsent(entity, k -> new HashMap<>());

		// Calculate when the cooldown will end
		long cooldownEnd = System.currentTimeMillis() + ability.getCooldown();

		// Store the cooldown end time
		entityCooldowns.put(ability.getId(), cooldownEnd);
	}

	/**
	 * Clear expired cooldowns for an entity
	 */
	public void cleanupExpiredCooldowns(RPGEntity entity) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns == null) {
			return;
		}

		long currentTime = System.currentTimeMillis();
		entityCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);

		// Remove empty cooldown maps
		if (entityCooldowns.isEmpty()) {
			cooldowns.remove(entity);
		}
	}

	/**
	 * Clear all cooldowns for an entity (useful for admin commands, respawn, etc.)
	 */
	public void clearAllCooldowns(RPGEntity entity) {
		cooldowns.remove(entity);
	}

	/**
	 * Clear a specific ability cooldown for an entity
	 */
	public void clearCooldown(RPGEntity entity, Ability ability) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns != null) {
			entityCooldowns.remove(ability.getId());

			// Clean up empty map
			if (entityCooldowns.isEmpty()) {
				cooldowns.remove(entity);
			}
		}
	}

	/**
	 * Get all active cooldowns for an entity (UI)
	 */
	public Map<String, Long> getActiveCooldowns(RPGEntity entity) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns == null) {
			return new HashMap<>();
		}

		Map<String, Long> activeCooldowns = new HashMap<>();
		long currentTime = System.currentTimeMillis();

		for (Map.Entry<String, Long> entry : entityCooldowns.entrySet()) {
			long remaining = entry.getValue() - currentTime;
			if (remaining > 0) {
				activeCooldowns.put(entry.getKey(), remaining);
			}
		}

		return activeCooldowns;
	}

	/**
	 * Reduce cooldown time for an ability (cooldown reduction mby)
	 */
	public void reduceCooldown(RPGEntity entity, Ability ability, long reductionMillis) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns == null) {
			return;
		}

		Long currentCooldownEnd = entityCooldowns.get(ability.getId());
		if (currentCooldownEnd == null) {
			return;
		}

		long newCooldownEnd = currentCooldownEnd - reductionMillis;
		long currentTime = System.currentTimeMillis();

		if (newCooldownEnd <= currentTime) {
			// Cooldown is completely removed
			entityCooldowns.remove(ability.getId());
			if (entityCooldowns.isEmpty()) {
				cooldowns.remove(entity);
			}
		} else {
			// Update with reduced cooldown
			entityCooldowns.put(ability.getId(), newCooldownEnd);
		}
	}

	@Override
	public void tick(long now) {

		// 1. Tick active effects and cleanup
		for (Map.Entry<RPGEntity, List<Effect>> entry : new ArrayList<>(activeEffects.entrySet())) {
			RPGEntity entity = entry.getKey();
			List<Effect> effects = entry.getValue();

			// Tick each effect
			for (Effect effect : new ArrayList<>(effects)) {
				effect.tick(entity, now);

				if (effect.hasExpired(now)) {
					effect.cancel(); // ensure cleanup
					effects.remove(effect);
				}
			}

			// Remove entity from map if no active effects left
			if (effects.isEmpty()) {
				activeEffects.remove(entity);
			}
		}

		// 2. Cleanup expired cooldowns
		for (RPGEntity entity : new ArrayList<>(cooldowns.keySet())) {
			cleanupExpiredCooldowns(entity);
		}
	}

	@Override
	public void cancelAll() {
		for (Map.Entry<RPGEntity, List<Effect>> entry : new ArrayList<>(activeEffects.entrySet())) {
			entry.getValue().forEach(effect -> effect.cancel());
			activeEffects.remove(entry.getKey());
		}
	}

}
