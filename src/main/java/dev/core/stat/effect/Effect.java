package dev.core.stat.effect;

import java.util.List;

import dev.core.entity.RPGEntity;
import dev.core.stat.StatModifier;

public interface Effect {

	String getId();

	String getSourceId();

	boolean isExpired(long now);

	void onApply(RPGEntity target, long now);

	void onRemove(RPGEntity target, long now);

	void onTick(RPGEntity target, long now);

	List<StatModifier> getModifiers(long now);

}
