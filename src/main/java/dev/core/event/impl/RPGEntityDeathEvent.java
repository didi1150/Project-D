package dev.core.event.impl;

import dev.core.entity.RPGEntity;

public class RPGEntityDeathEvent extends RPGEntityEvent{

	private final RPGEntity killer;

	public RPGEntityDeathEvent(RPGEntity entity) {
		this(entity, null);
	}

	/**
	 * @param entity The entity that died
	 * @param killer The entity that landed the killing blow (may be null for
	 *               environment deaths). A killer that is a player-owned summon
	 *               means the summon's owner deserves the credit.
	 */
	public RPGEntityDeathEvent(RPGEntity entity, RPGEntity killer) {
		super(entity);
		this.killer = killer;
	}

	public RPGEntity getKiller() {
		return killer;
	}
}