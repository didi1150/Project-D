package dev.core.status;

import java.util.ArrayList;
import java.util.List;

import dev.core.entity.RPGEntity;

/**
 * Default manager wired into RPG entities built without a status effect
 * manager (legacy constructors, tests). Accepts applies silently and keeps no
 * state.
 */
public final class NoopStatusEffectManager implements StatusEffectManagerInterface {

	private static final NoopStatusEffectManager INSTANCE = new NoopStatusEffectManager();

	private NoopStatusEffectManager() {
	}

	public static NoopStatusEffectManager getInstance() {
		return INSTANCE;
	}

	@Override
	public boolean apply(RPGEntity entity, StatusEffectType type, long durationMillis, boolean fadeOut,
			double potency) {
		return true;
	}

	@Override
	public boolean apply(RPGEntity entity, StatusEffectType type, long durationMillis) {
		return true;
	}

	@Override
	public boolean remove(RPGEntity entity, StatusEffectType type) {
		return false;
	}

	@Override
	public boolean has(RPGEntity entity, StatusEffectType type) {
		return false;
	}

	@Override
	public boolean hasHardCc(RPGEntity entity) {
		return false;
	}

	@Override
	public boolean isCcImmune(RPGEntity entity) {
		return false;
	}

	@Override
	public List<ActiveStatusEffect> getActive(RPGEntity entity) {
		return new ArrayList<>();
	}

	@Override
	public void removeAll(RPGEntity entity) {
	}

	@Override
	public void tick(long now) {
	}

	@Override
	public void cancelAll() {
	}
}