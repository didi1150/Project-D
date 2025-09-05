package dev.core.ability;

import dev.core.entity.RPGEntity;
import dev.core.event.Event;

public abstract class Effect {

	private final Event cancelEvent;
	private final long startTime; // when this effect started
	private final long duration; // how long this effect lasts (ms), <= 0 means infinite
	private final boolean singleInstance;
	private final String cooldownKey;

	public Effect(Event cancelEvent, long duration, boolean singleInstance, String cooldownKey) {
		this.cancelEvent = cancelEvent;
		this.startTime = System.currentTimeMillis();
		this.duration = duration;
		this.singleInstance = singleInstance;
		this.cooldownKey = cooldownKey;
	}

	/**
	 * Called once when the effect is applied to the caster. startCooldown.run()
	 * should be invoked when the effect decides it's appropriate to trigger
	 * cooldown.
	 */
	public abstract void cast(RPGEntity caster, Runnable startCooldown, Runnable resetCooldown);

	/**
	 * Called when the effect is forcefully ended (dispelled, interrupted, etc).
	 */
	public abstract void cancel();

	/**
	 * Optional per-tick update (damage over time, heal over time, particles, etc).
	 */
	public void tick(RPGEntity caster, long now) {
		// Default: do nothing
	}

	/**
	 * @return true if the effect duration has passed, false otherwise
	 */
	public boolean hasExpired(long now) {
		return duration > 0 && (now - startTime) >= duration;
	}

	public Event getCancelEvent() {
		return cancelEvent;
	}

	public long getStartTime() {
		return startTime;
	}

	public long getDuration() {
		return duration;
	}

	public boolean isSingleInstance() {
		return singleInstance;
	}

	public String getCooldownKey() {
		return cooldownKey;
	}
}
