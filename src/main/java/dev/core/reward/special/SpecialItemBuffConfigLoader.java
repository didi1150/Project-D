package dev.core.reward.special;

import java.util.ArrayList;
import java.util.List;

import dev.core.stat.StatTarget;
import dev.core.stat.StatType;
import dev.core.stat.modifier.ModifierStackPolicy;
import dev.core.stat.modifier.StatModifierType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/** Loads the configurable random buff pool used by special reward items. */
public final class SpecialItemBuffConfigLoader {

    public static final String FILE_NAME = "special-items.yml";

    private SpecialItemBuffConfigLoader() {
    }

    public static SpecialItemBuffConfig load(ConfigProvider provider) {
        List<SpecialItemBuff> buffs = new ArrayList<>();
        ConfigSection root = provider == null ? null : provider.getRoot();
        ConfigSection buffsSection = root == null ? null : root.getSection("buffs");
        if (buffsSection != null) {
            for (String id : buffsSection.getKeys()) {
                ConfigSection section = buffsSection.getSection(id);
                if (section == null) {
                    continue;
                }
                SpecialItemBuff buff = parse(id, section);
                if (buff != null) {
                    buffs.add(buff);
                }
            }
        }
        if (buffs.isEmpty()) {
            System.out.println("[SpecialItems] No usable buffs configured; using built-in defaults.");
            buffs = defaults();
        }
        return new SpecialItemBuffConfig(buffs);
    }

    private static SpecialItemBuff parse(String id, ConfigSection section) {
        String displayName = section.getString("display-name", id);
        double duration = section.getDouble("duration-seconds", 10.0);
        List<SpecialItemBuffStat> stats = new ArrayList<>();
        for (ConfigSection statSection : section.getSectionList("stat-modifiers")) {
            try {
                StatType statType = StatType.valueOf(
                        statSection.getString("statType", StatType.MOVE_SPEED.name()).trim().toUpperCase());
                double amount = statSection.getDouble("amount", 0);
                StatModifierType modifierType = StatModifierType.valueOf(statSection
                        .getString("modifierType", StatModifierType.FLAT.name()).trim().toUpperCase());
                ModifierStackPolicy policy = ModifierStackPolicy.valueOf(statSection
                        .getString("policy", ModifierStackPolicy.STACK.name()).trim().toUpperCase());
                StatTarget target = StatTarget.valueOf(
                        statSection.getString("statTarget", StatTarget.BOTH.name()).trim().toUpperCase());
                stats.add(new SpecialItemBuffStat(statType, amount, modifierType, policy, target));
            } catch (IllegalArgumentException e) {
                System.out.println("[SpecialItems] Skipping invalid stat modifier in buff '" + id + "': "
                        + e.getMessage());
            }
        }
        if (stats.isEmpty()) {
            System.out.println("[SpecialItems] Skipping buff '" + id + "' because it has no valid stat modifiers.");
            return null;
        }
        return new SpecialItemBuff(id, displayName, duration, stats);
    }

    public static List<SpecialItemBuff> defaults() {
        return List.of(
                new SpecialItemBuff("SWIFTNESS", "Swiftness", 10.0, List.of(
                        new SpecialItemBuffStat(StatType.MOVE_SPEED, 25.0, StatModifierType.FLAT,
                                ModifierStackPolicy.STACK, StatTarget.BOTH))),
                new SpecialItemBuff("MIGHT", "Might", 10.0, List.of(
                        new SpecialItemBuffStat(StatType.ATTACK_DAMAGE, 25.0, StatModifierType.FLAT,
                                ModifierStackPolicy.STACK, StatTarget.BOTH))),
                new SpecialItemBuff("FOCUS", "Focus", 10.0, List.of(
                        new SpecialItemBuffStat(StatType.ABILITY_HASTE, 25.0, StatModifierType.FLAT,
                                ModifierStackPolicy.STACK, StatTarget.BOTH))),
                new SpecialItemBuff("BULWARK", "Bulwark", 10.0, List.of(
                        new SpecialItemBuffStat(StatType.ARMOR, 25.0, StatModifierType.FLAT,
                                ModifierStackPolicy.STACK, StatTarget.BOTH),
                        new SpecialItemBuffStat(StatType.MAGIC_RESIST, 25.0, StatModifierType.FLAT,
                                ModifierStackPolicy.STACK, StatTarget.BOTH))),
                new SpecialItemBuff("LIFEDRAIN", "Lifedrain", 10.0, List.of(
                        new SpecialItemBuffStat(StatType.LIFESTEAL, 0.05, StatModifierType.FLAT,
                                ModifierStackPolicy.STACK, StatTarget.BOTH))),
                new SpecialItemBuff("RECOVERY", "Recovery", 10.0, List.of(
                        new SpecialItemBuffStat(StatType.HEALTH_REGEN, 10.0, StatModifierType.FLAT,
                                ModifierStackPolicy.STACK, StatTarget.BOTH))));
    }
}
