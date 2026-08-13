package dev.bukkit.game;

import dev.core.game.IClock;

public class BukkitClock implements IClock {

	@Override
	public long getCurrentTime() {
		return System.currentTimeMillis();
	}

}
