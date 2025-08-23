package dev.core.stat.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.core.entity.RPGEntity;
import dev.core.stat.StatModifier;

public class EffectManager {

	private List<Effect> activeEffects;
	private RPGEntity entity;

	public EffectManager(RPGEntity target) {
		this.entity = target;
		this.activeEffects = new ArrayList<Effect>();
	}

	public void addEffect(Effect effect, long now) {
		effect.onApply(entity, now);
		activeEffects.add(effect);
	}

	public void onTick(long now) {
		Iterator<Effect> it = activeEffects.iterator();
		while (it.hasNext()) {
			Effect effect = it.next();
			if (effect.isExpired(now)) {
				effect.onRemove(entity, now);
			}
		}
	}

	public List<StatModifier> gatherAllModifiers(long now) {
		return activeEffects.stream().filter(b -> !b.isExpired(now)).flatMap(b -> b.getModifiers(now).stream())
				.toList();
	}
}
