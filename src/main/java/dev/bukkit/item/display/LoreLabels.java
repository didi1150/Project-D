package dev.bukkit.item.display;

import java.util.HashMap;
import java.util.Map;

import dev.core.item.display.StyleTagParser;
import dev.core.item.display.TextColor;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * The lore presentation labels, loaded from {@code lore.yml} so wording,
 * colors and layout are editable without recompiling. Values are templates
 * with {@code {placeholder}} tokens and style tags (see the lore.yml header);
 * {@link #format} substitutes the placeholders and parses the tags into
 * legacy §-codes. Keys missing from the config fall back to the built-in
 * defaults, so a partial or absent lore.yml never breaks rendering.
 */
public final class LoreLabels {

    public static final String PASSIVE_STATS_HEADER = "passive-stats-header";
    public static final String ACTIVE_STATS_HEADER = "active-stats-header";
    public static final String ABILITIES_HEADER = "abilities-header";
    public static final String ABILITY_LABEL = "ability-label";
    public static final String PASSIVE_LABEL = "passive-label";
    public static final String ACTION_SUFFIX = "action-suffix";
    public static final String COST_LABEL = "cost-label";
    public static final String MANA_DISCOUNT_NOTE = "mana-discount-note";
    public static final String COOLDOWN_LABEL = "cooldown-label";
    public static final String COOLDOWN_SCALING_HASTE_SUFFIX = "cooldown-scaling-haste-suffix";
    public static final String COOLDOWN_SCALING_NONE_SUFFIX = "cooldown-scaling-none-suffix";
    public static final String COOLDOWN_SCOPE_PLAYER_NOTE = "cooldown-scope-player-note";
    public static final String COOLDOWN_SCOPE_ITEM_NOTE = "cooldown-scope-item-note";
    public static final String ITEM_SET_LINE = "item-set-line";
    public static final String SET_BONUS_LABEL = "set-bonus-label";
    public static final String LEVEL_LINE = "level-line";

    /** Built-in fallbacks, mirroring the pre-config rendering exactly. */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry(PASSIVE_STATS_HEADER, "<gray/>Passive Stats:"),
            Map.entry(ACTIVE_STATS_HEADER, "<gray/>Active Stats (Only applied when held):"),
            Map.entry(ABILITIES_HEADER, "<gold/>Item Abilities:"),
            Map.entry(ABILITY_LABEL, "<yellow/>Ability: {name}"),
            Map.entry(PASSIVE_LABEL, "<yellow/>Passive: {name}"),
            Map.entry(ACTION_SUFFIX, " <dark_gray/><bold/>{action}"),
            Map.entry(COST_LABEL, "<dark_gray/>Cost:"),
            Map.entry(MANA_DISCOUNT_NOTE, "<dark_purple/>Mana costs reduced by 10% (Mage Set)"),
            Map.entry(COOLDOWN_LABEL, "<dark_gray/>Cooldown: {cooldown}"),
            Map.entry(COOLDOWN_SCALING_HASTE_SUFFIX, " <gray/>(scales with Ability Haste)"),
            Map.entry(COOLDOWN_SCALING_NONE_SUFFIX, " <gray/>(fixed)"),
            Map.entry(COOLDOWN_SCOPE_PLAYER_NOTE, "<dark_gray/>Per-player cooldown"),
            Map.entry(COOLDOWN_SCOPE_ITEM_NOTE, "<dark_gray/>Per-item cooldown"),
            Map.entry(ITEM_SET_LINE, "<dark_purple/>Part of the {set} Set"),
            Map.entry(SET_BONUS_LABEL, "<gray/>{pieces}-Piece Bonus: "),
            Map.entry(LEVEL_LINE, "<gold/><bold/>LEVEL {level}: {type} ITEM"));

    private static final StyleTagParser PARSER = new StyleTagParser(TextColor.GRAY);

    private static volatile Map<String, String> labels = DEFAULTS;

    private LoreLabels() {
    }

    /**
     * Loads the {@code lore:} section of the given provider over the defaults.
     * Call once at startup (DMain); safe to call again to hot-reload.
     */
    public static void load(ConfigProvider provider) {
        ConfigSection lore = provider.getRoot().getSection("lore");
        if (lore == null || lore.getKeys().isEmpty()) {
            return;
        }
        Map<String, String> merged = new HashMap<>(DEFAULTS);
        for (String key : DEFAULTS.keySet()) {
            String value = lore.getString(key, null);
            if (value != null) {
                merged.put(key, value);
            }
        }
        labels = Map.copyOf(merged);
    }

    /** Restores the built-in defaults (test support). */
    public static void reset() {
        labels = DEFAULTS;
    }

    /**
     * Renders the label for {@code key}: substitutes {@code {placeholder}}
     * tokens from the alternating key/value pairs and parses style tags into
     * legacy §-codes.
     */
    public static String format(String key, Object... placeholderPairs) {
        String template = labels.getOrDefault(key, DEFAULTS.get(key));
        for (int i = 0; i + 1 < placeholderPairs.length; i += 2) {
            template = template.replace("{" + placeholderPairs[i] + "}",
                    String.valueOf(placeholderPairs[i + 1]));
        }
        StringBuilder sb = new StringBuilder();
        for (StyleTagParser.StyledSegment segment : PARSER.parse(template)) {
            sb.append(BukkitTextColorAdapter.toChatFormatting(segment.style()));
            sb.append(segment.text());
        }
        return sb.toString();
    }
}
