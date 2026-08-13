package dev.core.event.impl;

import dev.core.entity.RPGEntity;
import dev.core.event.CoreCancellable;

/**
 * Base class for any event that modifies health (damage, heal, shield).
 */
public abstract class RPGEntityModifyHealthEvent extends RPGEntityByEntityEvent implements CoreCancellable {

	protected double baseAmount; // unmodified base value
	protected double modifiedAmount; // can be changed by listeners
	protected boolean cancelled;

	protected RPGEntityModifyHealthEvent(RPGEntity source, RPGEntity target, double amount) {
		super(source, target);
		this.baseAmount = amount;
		this.modifiedAmount = amount;
	}

	/** Unmodified initial amount (for logging, debugging) */
	public double getBaseAmount() {
		return baseAmount;
	}

	/** Current amount (can be modified by listeners) */
	public double getAmount() {
		return modifiedAmount;
	}

	public void setAmount(double amount) {
		this.modifiedAmount = amount;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}
}
