package dev.core.ability;

import dev.core.entity.RPGEntity;

public abstract class Effect {

	public abstract void cast(RPGEntity caster, Runnable startCooldown);

	public abstract void cancel();
}
