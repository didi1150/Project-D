package dev.core.event.impl;

import dev.core.entity.RPGEntity;

public abstract class RPGEntityByEntityEvent extends RPGEntityEvent {

	private RPGEntity source;

	public RPGEntityByEntityEvent(RPGEntity source, RPGEntity entity) {
		super(entity);
		// TODO Auto-generated constructor stub
		this.source = source;
	}

	public RPGEntity getSource() {
		return source;
	}

}
