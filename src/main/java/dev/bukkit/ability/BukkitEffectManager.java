package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.ItemStackAdapter;
import dev.core.ability.Ability;
import dev.core.ability.CooldownScope;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.impl.ParticleTestAbility;
import dev.core.ability.impl.SwingBoneAbility;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;

public class BukkitEffectManager implements EffectManagerInterface {

	private Map<RPGEntity, Map<String, List<Effect>>> activeEffects;
	private Map<RPGEntity, Map<String, Long>> cooldowns;

	private static EffectManagerInterface instance;

	private BukkitEffectManager() {
		this.activeEffects = new HashMap<RPGEntity, Map<String, List<Effect>>>();
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

		String cooldownKey = getCooldownKey(entity, ability);

		if (ability instanceof ParticleTestAbility) {
			effect = new BukkitParticleTestEffect(cooldownKey);
			effect.cast(entity, () -> setCooldown(entity, ability, cooldownKey), () -> {
			});
		}

		if (ability instanceof SwingBoneAbility) {
			effect = new BukkitSwingBoneEffect(cooldownKey);
			if (entity instanceof BukkitPlayerEntity) {
				String effectKey = getEffectKey(entity, ability);
				List<Effect> effects = activeEffects.getOrDefault(entity, new HashMap<>()).getOrDefault(effectKey,
						new ArrayList<>());

				boolean alreadyActive = effects.stream().anyMatch(e -> e instanceof BukkitSwingBoneEffect);

				if (!alreadyActive) {
					effect.cast(entity, () -> setCooldown(entity, ability, cooldownKey),
							() -> reduceCooldown(entity, ability, ability.getCooldown(), cooldownKey));
				} else {
					return effect;
				}
			}
		}

		if (effect != null) {
			String effectKey = getEffectKey(entity, ability);

			Map<String, List<Effect>> effectsByKey = activeEffects.computeIfAbsent(entity, k -> new HashMap<>());
			List<Effect> effectsList = effectsByKey.computeIfAbsent(effectKey, k -> new ArrayList<>());

			effectsList.add(effect);
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

	private String getEffectKey(RPGEntity entity, Ability ability) {
		if (ability.getScope() == CooldownScope.PLAYER) {
			return ability.getId();
		}

		// ITEM scope
		if (entity instanceof BukkitPlayerEntity playerEntity) {
			Player player = playerEntity.getPlayer();
			return player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer()
					.get(ItemStackAdapter.UUID_ID_KEY, PersistentDataType.STRING);
		}

		return ability.getId();
	}

	public long remainingCooldown(RPGEntity entity, Ability ability) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns == null) {
			return 0; // No cooldowns for this entity
		}

		String cooldownKey = getCooldownKey(entity, ability);
		Long cooldownEnd = entityCooldowns.get(cooldownKey);
		if (cooldownEnd == null) {
			return 0; // No cooldown for this specific ability
		}

		long currentTime = System.currentTimeMillis();
		long remaining = Math.max(0, cooldownEnd - currentTime); // Return 0 if cooldown has expired
		if (remaining == 0) {
			// Remove from cooldown list if cooldowns expire
			cooldowns.get(entity).remove(cooldownKey);
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
	private void setCooldown(RPGEntity entity, Ability ability, String key) {
		Map<String, Long> entityCooldowns = cooldowns.computeIfAbsent(entity, k -> new HashMap<>());
		long cooldownEnd = System.currentTimeMillis() + ability.getCooldown();
		entityCooldowns.put(key, cooldownEnd);
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
			String key = getCooldownKey(entity, ability);
			entityCooldowns.remove(key);
			if (entityCooldowns.isEmpty()) {
				cooldowns.remove(entity);
			}
		}
	}

	private String getCooldownKey(RPGEntity entity, Ability ability) {
		if (ability.getScope() == CooldownScope.PLAYER) {
			return ability.getId();
		}

		// ITEM scope
		if (entity instanceof BukkitPlayerEntity playerEntity) {
			Player player = playerEntity.getPlayer();
			return player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer()
					.get(ItemStackAdapter.UUID_ID_KEY, PersistentDataType.STRING);
		}

		// Fallback to ability id if no UUID
		return ability.getId();
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
	public void reduceCooldown(RPGEntity entity, Ability ability, long reductionMillis, String key) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns == null)
			return;

		Long currentCooldownEnd = entityCooldowns.get(key);
		if (currentCooldownEnd == null)
			return;

		long newCooldownEnd = currentCooldownEnd - reductionMillis;
		long currentTime = System.currentTimeMillis();

		if (newCooldownEnd <= currentTime) {
			entityCooldowns.remove(key);
			if (entityCooldowns.isEmpty()) {
				cooldowns.remove(entity);
			}
		} else {
			entityCooldowns.put(key, newCooldownEnd);
		}
	}

	@Override
	public void tick(long now) {

		for (Map.Entry<RPGEntity, Map<String, List<Effect>>> entry : new ArrayList<>(activeEffects.entrySet())) {
			RPGEntity entity = entry.getKey();
			Map<String, List<Effect>> effectsByKey = entry.getValue();

			for (Map.Entry<String, List<Effect>> keyEntry : new ArrayList<>(effectsByKey.entrySet())) {
				List<Effect> effects = keyEntry.getValue();

				for (Effect effect : new ArrayList<>(effects)) {
					effect.tick(entity, now);

					if (effect.hasExpired(now)) {
						effect.cancel();
						effects.remove(effect);
					}
				}

				if (effects.isEmpty()) {
					effectsByKey.remove(keyEntry.getKey());
				}
			}

			if (effectsByKey.isEmpty()) {
				activeEffects.remove(entity);
			}
		}

		// cleanup cooldowns
		for (RPGEntity entity : new ArrayList<>(cooldowns.keySet())) {
			cleanupExpiredCooldowns(entity);
		}
	}

	@Override
	public void cancelAll() {
		for (Map<String, List<Effect>> effectsByKey : new ArrayList<>(activeEffects.values())) {
			for (List<Effect> effects : effectsByKey.values()) {
				effects.forEach(Effect::cancel);
			}
		}
		activeEffects.clear();
	}

}
