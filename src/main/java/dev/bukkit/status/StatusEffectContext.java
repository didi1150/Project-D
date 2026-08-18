package dev.bukkit.status;

import org.bukkit.entity.LivingEntity;

import dev.core.entity.RPGEntity;
import dev.core.status.ActiveStatusEffect;

/**
 * Everything a {@link StatusEffectBehavior} needs to manipulate the affected
 * entity: the RPG facade and its vanilla backing entity, plus the effect
 * instance (for remaining time / potency).
 */
public final class StatusEffectContext {

	private final RPGEntity rpgEntity;
	private final LivingEntity livingEntity;
	private final ActiveStatusEffect effect;

	public StatusEffectContext(RPGEntity rpgEntity, LivingEntity livingEntity, ActiveStatusEffect effect) {
		this.rpgEntity = rpgEntity;
		this.livingEntity = livingEntity;
		this.effect = effect;
	}

	public RPGEntity getRpgEntity() {
		return rpgEntity;
	}

	public LivingEntity getLivingEntity() {
		return livingEntity;
	}

	public ActiveStatusEffect getEffect() {
		return effect;
	}
}