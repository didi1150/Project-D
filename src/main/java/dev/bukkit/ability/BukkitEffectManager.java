package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dev.core.ability.Ability;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.HealAbility;
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

		for (Entry<String, Double> entry : ability.getCost().getResourceCosts().entrySet()) {
			entity.getStatManager().modifyStat(StatType.valueOf(entry.getKey()), entry.getValue());
		}

		if (ability instanceof HealAbility) {
			BukkitHealAbility bukkitHealAbility = new BukkitHealAbility();
			bukkitHealAbility.cast(entity, () -> setCooldown(entity, ability));
			List<Effect> effectsList = activeEffects.getOrDefault(entity, new ArrayList<Effect>());
			effectsList.add(bukkitHealAbility);
			activeEffects.put(entity, effectsList);
			return bukkitHealAbility;
		}
		return null;
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
		return remainingCooldown(entity, ability) > 0;
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
	 * Clear expired cooldowns for an entity (optional cleanup method)
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
		
	}

}
