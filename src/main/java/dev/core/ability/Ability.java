package dev.core.ability;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

import dev.core.event.Event;
import dev.core.ability.storage.AbilityLoader;

@Getter
@Setter
public abstract class Ability {

	/**
	 * Override to provide per-holder mutable state for this ability. One instance
	 * is created per {@link ActiveAbility} binding. Return {@code null} when the
	 * ability is stateless.
	 */
	public Object createState() {
		return null;
	}

	/**
	 * Called when this ability becomes active for a holder (equip or set-bonus
	 * grant). The holder is available via {@code ctx.getHolder()}; per-holder
	 * state via {@code ctx.getState()}; event subscriptions via
	 * {@code ctx.getSubscriptions()}. Default is no-op.
	 */
	public void onActivate(ActiveAbility ctx) {
	}

	/**
	 * Called when this ability is removed from a holder (unequip, set break,
	 * death, quit). Behavior instances are torn down before this is invoked.
	 */
	public void onDeactivate(ActiveAbility ctx) {
	}

	private final String id;
	private String name;
	private List<String> description;
	private AbilityTriggerType triggerType;
	/**
	 * Only set when the ability is automatic
	 */
	private Event triggerEvent;
	private AbilityAction action;
	private CooldownScope scope;
	private long cooldown;
	/**
	 * Whether the caster's ABILITY_HASTE stat shortens this ability's cooldown
	 * ({@link CooldownScaling#HASTE}) or the configured cooldown is applied
	 * as-is ({@link CooldownScaling#NONE}).
	 */
	private CooldownScaling cooldownScaling = CooldownScaling.HASTE;
	/**
	 * The cast cost, configured EXCLUSIVELY in abilities.yml ({@code cost:}
	 * section, flat {@code amount} or dynamic {@code formula}). Java code
	 * never defines costs; an ability without a config cost is free to cast.
	 * Written by {@link AbilityLoader} at startup.
	 */
	private AbilityCost cost;
	/**
	 * Whether this ability, when cast by a mob, is aimed at players (e.g.
	 * projectiles). {@code false} for self-buffs/heals that a mob may keep
	 * casting even when no player is in sight. Only consulted for mob casting;
	 * set via {@code targetsPlayer} in abilities.yml (defaults to {@code true}).
	 */
	private boolean targetsPlayer = true;

	public Ability(String id, Event triggerEvent) {
		this.id = id;
		this.triggerEvent = triggerEvent;
		this.cost = AbilityCost.noCost();
	}

	public Ability(String id) {
		this(id, null);
	}
}
