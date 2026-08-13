package dev.core.ability;

import dev.core.entity.RPGEntity;

public interface EffectManagerInterface {

	Effect cast(RPGEntity entity, Ability ability);

	boolean canActivate(RPGEntity entity, Ability ability);

	long remainingCooldown(RPGEntity entity, Ability ability);

	void tick(long now);

	void cancelAll();
}
