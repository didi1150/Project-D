package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.ManaDiscountUtils;
import dev.core.ability.Ability;
import dev.bukkit.hud.HudResourceFeedback;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.CooldownScope;
import dev.core.ability.CooldownScaling;
import dev.core.ability.CooldownSink;
import dev.core.ability.CostEntry;
import dev.core.ability.CostMode;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
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
		List<Charge> charges;
		try {
			charges = resolveCharges(entity, ability);
		} catch (IllegalArgumentException e) {
			System.out.println("Cost evaluation failed for ability " + ability.getId() + ": " + e.getMessage());
			return null;
		}
		for (Charge charge : charges) {
			entity.getStatManager().modifyStat(charge.resource(), -charge.amount());
		}

		String cooldownKey = getCooldownKey(entity, ability);

		Effect effect = AbilityRegistry.createEffect(ability.getId(), cooldownKey);
		if (effect == null) {
			return null; // registry already logged the warning
		}

		CooldownSink cooldownSink = new BukkitCooldownSink(this, entity, ability, cooldownKey);

		if (effect.isSingleInstance() && entity instanceof BukkitPlayerEntity) {
			String effectKey = getEffectKey(entity, ability);
			Map<String, List<Effect>> effectsByKey = activeEffects.computeIfAbsent(entity, k -> new HashMap<>());
			List<Effect> effects = effectsByKey.computeIfAbsent(effectKey, k -> new ArrayList<>());

			// Purge already-expired effects from the single-instance slot. This
			// manager's tick() is only driven from a few game states, so an expired
			// effect otherwise lingers forever and silently blocks every later cast
			// of the same ability (the "right-click does nothing" failure).
			long now = System.currentTimeMillis();
			effects.removeIf(e -> e.hasExpired(now));
			if (effects.isEmpty()) {
				effectsByKey.remove(effectKey);
			}

			// Single-instance effects are locked per EFFECT KEY (for ITEM-scoped
			// abilities that is the item instance's UUID), so a second cast is
			// refused only while the SAME key already has a live effect. Each
			// bonemerang instance therefore holds its own single-instance slot:
			// two different bonemerangs can be in flight at the same time, while
			// re-casting the same item mid-flight is still refused. PLAYER-scoped
			// abilities key by ability id, so their single-instance behavior is
			// unchanged.
			boolean alreadyActive = effects.stream()
					.anyMatch(e -> e.getClass() == effect.getClass() && !e.hasExpired(now));

			if (!alreadyActive) {
				effect.cast(entity, cooldownSink);
			} else {
				return effect;
			}
		} else {
			effect.cast(entity, cooldownSink);
		}

		String effectKey = getEffectKey(entity, ability);

		Map<String, List<Effect>> effectsByKey = activeEffects.computeIfAbsent(entity, k -> new HashMap<>());
		List<Effect> effectsList = effectsByKey.computeIfAbsent(effectKey, k -> new ArrayList<>());

		effectsList.add(effect);
		return effect;
	}

	@Override
	public boolean canActivate(RPGEntity entity, Ability ability) {
		if (isOnCooldown(entity, ability)) {
			return false;
		}

		try {
			for (Charge charge : resolveCharges(entity, ability)) {
				if (entity.getStatManager().getCurrentValue(charge.resource(), System.currentTimeMillis()) < charge
						.amount()) {
					// HUD-transient "Not enough <resource>" feedback (auto StatType color, debounced, chat fallback if hud disabled)
					if (entity instanceof BukkitPlayerEntity bpe) {
						bpe.getPlayer().ifPresent(p -> {
							try {
								CostMode mode = CostMode.fromResourceType(charge.resource().name());
								HudResourceFeedback.send(p, mode);
							} catch (Exception ignored) {}
						});
					}
					return false;
				}
			}
		} catch (IllegalArgumentException e) {
			System.out.println("Cost evaluation failed for ability " + ability.getId() + ": " + e.getMessage());
			return false;
		}
		// TODO: Other clauses
		return true;
	}

	/**
	 * Returns the first insufficient resource mode for this cast, or empty if costs can be paid / on cooldown.
	 * Exposed for callers that want custom handling without side-effect.
	 */
	public java.util.Optional<CostMode> insufficientResource(RPGEntity entity, Ability ability) {
		if (isOnCooldown(entity, ability)) return java.util.Optional.empty();
		try {
			for (Charge charge : resolveCharges(entity, ability)) {
				if (entity.getStatManager().getCurrentValue(charge.resource(), System.currentTimeMillis()) < charge.amount()) {
					return java.util.Optional.of(CostMode.fromResourceType(charge.resource().name()));
				}
			}
		} catch (IllegalArgumentException e) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.empty();
	}

	/**
	 * The costs an ability would charge this caster right now: each entry's
	 * dynamic/flat amount resolved against the caster, mana-discount applied.
	 * Resolved up front (before any deduction) so a failing formula aborts the
	 * cast without partially draining resources. Throws
	 * {@link IllegalArgumentException} when a formula references an unknown
	 * variable or evaluates non-finite.
	 */
	private List<Charge> resolveCharges(RPGEntity entity, Ability ability) {
		List<Charge> charges = new ArrayList<>();
		for (CostEntry cost : ability.getCost().getCosts()) {
			double base = cost.resolve(entity);
			double amount = ManaDiscountUtils.discountedCost(entity, cost.mode().getResourceType(), base);
			charges.add(new Charge(StatType.valueOf(cost.mode().getResourceType()), amount));
		}
		return charges;
	}

	/**
	 * One resolved resource charge: the stat to drain and the discounted amount
	 * to drain it by.
	 */
	private record Charge(StatType resource, double amount) {
	}

	private String getEffectKey(RPGEntity entity, Ability ability) {
		return itemScopeKey(entity, ability);
	}

	private String itemScopeKey(RPGEntity entity, Ability ability) {
		if (ability.getScope() == CooldownScope.PLAYER) {
			return ability.getId();
		}

		// ITEM scope: key by the held item's instance UUID so each item has its own
		// effect slot/cooldown. Fall back to the ability id when there is no
		// identifiable RPG item (air/vanilla hand without our UUID PDC, offline
		// player, non-player entity) so keys are never null.
		if (entity instanceof BukkitPlayerEntity playerEntity) {
			Player player = playerEntity.getPlayer().orElse(null);
			if (player != null) {
				ItemStack item = player.getInventory().getItemInMainHand();
				if (item != null && item.hasItemMeta() && item.getItemMeta() != null) {
					String uuid = item.getItemMeta().getPersistentDataContainer()
							.get(BukkitItemStackAdapter.UUID_ID_KEY, PersistentDataType.STRING);
					if (uuid != null) {
						return uuid;
					}
				}
			}
		}

		return ability.getId();
	}

	public long remainingCooldown(RPGEntity entity, Ability ability) {
		return remainingCooldown(entity, getCooldownKey(entity, ability));
	}

	/**
	 * Keyed lookup used by {@link BukkitCooldownSink}: the key was cached at
	 * cast time, so it stays correct even if the caster swaps items while an
	 * effect is active.
	 */
	public long remainingCooldown(RPGEntity entity, String key) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns == null) {
			return 0; // No cooldowns for this entity
		}

		Long cooldownEnd = entityCooldowns.get(key);
		if (cooldownEnd == null) {
			return 0; // No cooldown for this specific ability
		}

		long currentTime = System.currentTimeMillis();
		long remaining = Math.max(0, cooldownEnd - currentTime); // Return 0 if cooldown has expired
		if (remaining == 0) {
			// Remove from cooldown list if cooldowns expire
			entityCooldowns.remove(key);
			if (entityCooldowns.isEmpty()) {
				cooldowns.remove(entity);
			}
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
	void setCooldown(RPGEntity entity, Ability ability, String key) {
		setCooldown(entity, ability, key, scaledCooldownMillis(entity, ability));
	}

	/**
	 * Set a raw custom cooldown duration. No scaling is applied; the caller
	 * owns any stat interaction (e.g. a shatter penalty).
	 */
	void setCooldown(RPGEntity entity, Ability ability, String key, long millis) {
		Map<String, Long> entityCooldowns = cooldowns.computeIfAbsent(entity, k -> new HashMap<>());
		long cooldownEnd = System.currentTimeMillis() + millis;
		entityCooldowns.put(key, cooldownEnd);
	}

	/**
	 * Derive the actual cooldown duration from the configured value: abilities
	 * with {@link CooldownScaling#HASTE} are shortened by the caster's
	 * ABILITY_HASTE stat (base * 100 / (100 + haste)); {@code NONE} abilities
	 * keep the configured duration as-is.
	 */
	private long scaledCooldownMillis(RPGEntity entity, Ability ability) {
		long base = ability.getCooldown();
		if (ability.getCooldownScaling() != CooldownScaling.HASTE) {
			return base;
		}
		double haste = Math.max(0, entity.getStatEngineAdapter()
				.getCurrentValue(StatType.ABILITY_HASTE, System.currentTimeMillis()));
		return (long) (base * 100L / (100 + haste));
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
		clearCooldown(entity, getCooldownKey(entity, ability));
	}

	/**
	 * Keyed variant used by {@link BukkitCooldownSink} so the cached cast-time
	 * key is honored even if the caster's current item differs.
	 */
	public void clearCooldown(RPGEntity entity, String key) {
		Map<String, Long> entityCooldowns = cooldowns.get(entity);
		if (entityCooldowns != null) {
			entityCooldowns.remove(key);
			if (entityCooldowns.isEmpty()) {
				cooldowns.remove(entity);
			}
		}
	}

	private String getCooldownKey(RPGEntity entity, Ability ability) {
		return itemScopeKey(entity, ability);
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
