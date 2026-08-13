package dev.core.event.impl;

import dev.core.entity.RPGEntity;

public class RPGEntityDeathEvent extends RPGEntityEvent{

	public RPGEntityDeathEvent(RPGEntity entity) {
		super(entity);
	}
}
