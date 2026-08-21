package dev.core.stat.loader;

import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.descriptor.StatColor;
import dev.core.stat.descriptor.StatDescriptor;
import dev.core.stat.descriptor.StatRegistry;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Loads the optional {@code statMetadata} section of stats.yml and applies it
 * as overrides to the StatRegistry. Any field of a stat descriptor
 * (display-name, symbol, color, percent) can be overridden; omitted fields
 * fall back to the values seeded from {@link StatTypeAdapter}.
 * 
 * <p>Section keys are stat ids (e.g. {@code core:attack_damage}); the short
 * name (e.g. {@code attack_damage}) is accepted as well.
 */
public final class StatMetadataLoader {

    private StatMetadataLoader() {
    }

    /**
     * Applies stat metadata overrides from the {@code statMetadata} section of
     * the given config. Unknown stat ids are skipped (a warning is logged).
     */
    public static void loadStatMetadata(ConfigProvider provider) {
        ConfigSection root = provider.getRoot().getSection("statMetadata");
        if (root == null) {
            return;
        }
        StatRegistry registry = StatRegistry.getInstance();
        for (String key : root.getKeys()) {
            ConfigSection section = root.getSection(key);
            if (section == null) {
                continue;
            }
            String statId = normalizeId(key);
            StatDescriptor current = registry.get(statId).orElse(null);
            if (current == null) {
                System.err.println("[statMetadata] Unknown stat id '" + key + "' — skipping override");
                continue;
            }
            apply(registry, statId, current, section);
        }
    }

    private static void apply(StatRegistry registry, String statId, StatDescriptor current, ConfigSection section) {
        String displayName = section.getString("display-name", null);
        String symbol = section.getString("symbol", null);
        String colorRaw = section.getString("color", null);
        boolean percent = section.getBoolean("percent", current.isPercent());

        StatColor color = StatColor.fromString(colorRaw);
        if (color == null && colorRaw != null) {
            System.err.println("[statMetadata] Invalid color '" + colorRaw + "' for '" + statId
                    + "' — keeping existing color");
            color = current.getColor();
        }

        registry.override(new StatDescriptor(
                statId,
                displayName != null ? displayName : current.getDisplayName(),
                symbol != null ? symbol : current.getSymbol(),
                color != null ? color : current.getColor(),
                current.getCategory(),
                percent));
    }

    /**
     * Accepts both fully qualified ids ("core:attack_damage") and short names
     * ("attack_damage").
     */
    private static String normalizeId(String key) {
        if (key.indexOf(':') >= 0) {
            return key;
        }
        return "core:" + key;
    }
}