package dev.core.event.impl;

import dev.core.entity.RPGEntity;

public class RPGEntityHealEvent extends RPGEntityModifyHealthEvent {
	private final HealReason reason;

	public RPGEntityHealEvent(RPGEntity source, RPGEntity target, double amount, HealReason reason) {
		super(source, target, amount);
		this.reason = reason;
	}

	public HealReason getReason() {
		return reason;
	}

	public enum HealReason {
		SPELL, // healing spell or ability
		ITEM, // potion, consumable
		LIFESTEAL, // from dealing damage
		REGENERATION, // natural regen tick
		EFFECT, // buff / potion effect
		COMMAND, // admin or console
		OTHER // fallback
	}
}
