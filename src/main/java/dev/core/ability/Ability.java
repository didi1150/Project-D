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

	public Ability(String id, Event triggerEvent, AbilityCost abilityCost) {
		this.id = id;
		this.triggerEvent = triggerEvent;
		this.cost = abilityCost;
	}

	public Ability(String id) {
		this(id, null, AbilityCost.noCost());
	}
}