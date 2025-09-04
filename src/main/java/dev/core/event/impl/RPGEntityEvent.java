package dev.core.event.impl;

import dev.core.entity.RPGEntity;
import dev.core.event.Event;

public abstract class RPGEntityEvent extends Event {

	protected RPGEntity entity;

	public RPGEntityEvent(RPGEntity entity) {
		this.entity = entity;
	}

	public RPGEntity getTarget() {
		return entity;
	}

}
