package dev.core.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import dev.core.entity.RPGEntity;

/**
 * Pure-Java status effect bookkeeping: per-entity active effect lists (in
 * application order), duration expiry, and the crowd-control stacking rules.
 *
 * <p>
 * CC rules:
 * <ul>
 * <li>Re-applying the same type refreshes its duration; identical effects never
 * stack with themselves.</li>
 * <li>Hard CC ({@link CcCategory#HARD}) clears all soft CC on apply; only one
 * hard CC may be active at a time (a different hard CC is rejected).</li>
 * <li>CC immunity ({@link CcCategory#IMMUNITY}) cleanses every active CC when
 * applied, then rejects every other CC while active.</li>
 * </ul>
 *
 * <p>
 * Subclasses hook {@link #onEffectApplied(RPGEntity, ActiveStatusEffect)} and
 * {@link #onEffectRemoved(RPGEntity, ActiveStatusEffect)} to attach/detach
 * entity-side state (Bukkit: vanilla behaviors + text displays).
 */
public class StatusEffectManager implements StatusEffectManagerInterface {

	protected final Map<RPGEntity, List<ActiveStatusEffect>> activeEffects = new HashMap<>();

	@Override
	public boolean apply(RPGEntity entity, StatusEffectType type, long durationMillis, boolean fadeOut, double potency) {
		List<ActiveStatusEffect> effects = activeEffects.computeIfAbsent(entity, k -> new ArrayList<>());
		long now = System.currentTimeMillis();

		if (type.getCategory() == CcCategory.IMMUNITY) {
			// Cleanses everything (including any prior immunity) before landing.
			effects.removeIf(e -> !e.getType().equals(type) && removeAfterNotify(entity, e));
			effects.removeIf(e -> e.getType().equals(type) && removeAfterNotify(entity, e));
			return addEffect(entity, effects, type, now, durationMillis, fadeOut, potency);
		}

		if (isCcImmune(entity)) {
			return false; // immunity blocks ALL cc
		}

		if (effects.stream().anyMatch(e -> e.getType().equals(type))) {
			// Same-type re-apply: replace with a fresh duration.
			effects.removeIf(e -> e.getType().equals(type) && removeAfterNotify(entity, e));
			return addEffect(entity, effects, type, now, durationMillis, fadeOut, potency);
		}

		if (type.getCategory() == CcCategory.HARD) {
			boolean anotherHardActive = effects.stream()
					.anyMatch(e -> e.getType().getCategory() == CcCategory.HARD);
			if (anotherHardActive) {
				return false; // only one hard CC at a time
			}
			// Hard CC overrides soft CC.
			effects.removeIf(e -> e.getType().getCategory() == CcCategory.SOFT && removeAfterNotify(entity, e));
			return addEffect(entity, effects, type, now, durationMillis, fadeOut, potency);
		}

		// Soft CC: coexists with other soft CC (slowed + rooted stack).
		return addEffect(entity, effects, type, now, durationMillis, fadeOut, potency);
	}

	@Override
	public boolean apply(RPGEntity entity, StatusEffectType type, long durationMillis) {
		return apply(entity, type, durationMillis, type.isFadeOutByDefault(), 1.0);
	}

	@Override
	public boolean remove(RPGEntity entity, StatusEffectType type) {
		List<ActiveStatusEffect> effects = activeEffects.get(entity);
		if (effects == null) {
			return false;
		}
		boolean removed = effects.removeIf(e -> e.getType().equals(type) && removeAfterNotify(entity, e));
		if (effects.isEmpty()) {
			activeEffects.remove(entity);
		}
		return removed;
	}

	@Override
	public boolean has(RPGEntity entity, StatusEffectType type) {
		List<ActiveStatusEffect> effects = activeEffects.get(entity);
		return effects != null && effects.stream().anyMatch(e -> e.getType().equals(type));
	}

	@Override
	public boolean hasHardCc(RPGEntity entity) {
		List<ActiveStatusEffect> effects = activeEffects.get(entity);
		return effects != null
				&& effects.stream().anyMatch(e -> e.getType().getCategory() == CcCategory.HARD);
	}

	@Override
	public boolean isCcImmune(RPGEntity entity) {
		List<ActiveStatusEffect> effects = activeEffects.get(entity);
		return effects != null && effects.stream()
				.anyMatch(e -> e.getType().getCategory() == CcCategory.IMMUNITY);
	}

	@Override
	public List<ActiveStatusEffect> getActive(RPGEntity entity) {
		List<ActiveStatusEffect> effects = activeEffects.get(entity);
		return effects == null ? new ArrayList<>() : new ArrayList<>(effects);
	}

	@Override
	public void removeAll(RPGEntity entity) {
		List<ActiveStatusEffect> effects = activeEffects.remove(entity);
		if (effects != null) {
			for (ActiveStatusEffect effect : effects) {
				onEffectRemoved(entity, effect);
			}
		}
	}

	@Override
	public void tick(long now) {
		Iterator<Map.Entry<RPGEntity, List<ActiveStatusEffect>>> it = activeEffects.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<RPGEntity, List<ActiveStatusEffect>> entry = it.next();
			RPGEntity entity = entry.getKey();
			List<ActiveStatusEffect> effects = entry.getValue();
			effects.removeIf(e -> e.hasExpired(now) && removeAfterNotify(entity, e));
			if (effects.isEmpty()) {
				it.remove();
			}
		}
	}

	@Override
	public void cancelAll() {
		for (Map.Entry<RPGEntity, List<ActiveStatusEffect>> entry : activeEffects.entrySet()) {
			for (ActiveStatusEffect effect : entry.getValue()) {
				onEffectRemoved(entry.getKey(), effect);
			}
		}
		activeEffects.clear();
	}

	/**
	 * Fires the removal hook and returns {@code true} so callers can use it as
	 * a {@code removeIf} predicate.
	 */
	private boolean removeAfterNotify(RPGEntity entity, ActiveStatusEffect effect) {
		onEffectRemoved(entity, effect);
		return true;
	}

	private boolean addEffect(RPGEntity entity, List<ActiveStatusEffect> effects, StatusEffectType type, long now,
			long durationMillis, boolean fadeOut, double potency) {
		ActiveStatusEffect effect = new ActiveStatusEffect(type, now,
				durationMillis <= 0 ? -1 : now + durationMillis, fadeOut, potency);
		effects.add(effect);
		onEffectApplied(entity, effect);
		return true;
	}

	/**
	 * Hook: called right after an effect is applied to the entity. Default no-op.
	 */
	protected void onEffectApplied(RPGEntity entity, ActiveStatusEffect effect) {
	}

	/**
	 * Hook: called when an effect ends (expiry, cleanse, removal, cancelAll).
	 * Default no-op.
	 */
	protected void onEffectRemoved(RPGEntity entity, ActiveStatusEffect effect) {
	}
}