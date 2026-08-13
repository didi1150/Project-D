package dev.core.entity.mob;

/**
 * A potion effect applied to a spawned mob, taken from a mob definition's
 * {@code effects} list. {@code durationTicks} of {@code -1} means infinite.
 */
public record MobEffect(String type, int amplifier, int durationTicks) {
}
