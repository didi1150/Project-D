package dev.core.reward.special;

import dev.core.stat.StatTarget;
import dev.core.stat.StatType;
import dev.core.stat.modifier.ModifierStackPolicy;
import dev.core.stat.modifier.StatModifierType;

/** One temporary stat modifier granted by a special-item buff. */
public record SpecialItemBuffStat(StatType statType, double amount, StatModifierType modifierType,
        ModifierStackPolicy stackPolicy, StatTarget statTarget) {
}
