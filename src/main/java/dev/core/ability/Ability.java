package dev.core.ability;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

import dev.core.event.Event;

@Getter
@Setter
public abstract class Ability {

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
	private AbilityCost cost;
	/**
	 * Whether this ability, when cast by a mob, is aimed at players (e.g.
	 * projectiles). {@code false} for self-buffs/heals that a mob may keep
	 * casting even when no player is in sight. Only consulted for mob casting;
	 * set via {@code targetsPlayer} in abilities.yml (defaults to {@code true}).
	 */
	private boolean targetsPlayer = true;

	public Ability(String id, Event triggerEvent, AbilityCost abilityCost) {
		this.id = id;
		this.triggerEvent = triggerEvent;
		this.cost = abilityCost != null ? abilityCost : AbilityCost.noCost();
	}

	public Ability(String id) {
		this(id, null, AbilityCost.noCost());
	}
}