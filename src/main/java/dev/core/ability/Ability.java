package dev.core.ability;

import java.util.List;

import dev.core.event.Event;

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
		cost = abilityCost;
	}

	public Ability(String id) {
		this(id, null, AbilityCost.noCost());
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getDescription() {
		return description;
	}

	public void setDescription(List<String> description) {
		this.description = description;
	}

	public AbilityTriggerType getTriggerType() {
		return triggerType;
	}

	public void setTriggerType(AbilityTriggerType triggerType) {
		this.triggerType = triggerType;
	}

	public Event getTriggerEvent() {
		return triggerEvent;
	}

	public AbilityAction getAction() {
		return action;
	}

	public void setAction(AbilityAction action) {
		this.action = action;
	}

	public CooldownScope getScope() {
		return scope;
	}

	public void setScope(CooldownScope scope) {
		this.scope = scope;
	}

	/**
	 * Cooldown in millis
	 */
	public long getCooldown() {
		return cooldown;
	}

	public void setCooldown(long cooldown) {
		this.cooldown = cooldown;
	}

	public AbilityCost getCost() {
		return cost;
	}

	public void setCost(AbilityCost cost) {
		this.cost = cost;
	}
}