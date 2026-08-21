package dev.core.entity.mob;

/**
 * An ability id referencing a {@code dev.core.ability.Effect} implementation
 * (registered in {@code BukkitEffectRegistry}, e.g. {@code BONE_SWING}) that
 * is cast on a spawned mob. Taken from a mob definition's {@code effects}
 * list. Vanilla potion effect names are not valid here.
 */
public record MobEffect(String effectId) {
}
