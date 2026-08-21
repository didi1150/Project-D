package dev.core.stat.descriptor;

import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a stat with its metadata: display name, symbol, color, and
 * formatting logic. This replaces direct usage of StatType enum for
 * extensibility.
 */
public class StatDescriptor {

    private final String id;
    private final String displayName;
    private final String symbol;
    private final StatColor color;
    private final StatCategory category;
    private final boolean percent;
    private final BiFunction<Double, Boolean, String> formatter; // (value, showPlus) -> formatted string

    /**
     * Creates a descriptor using the default formatting logic (percent-aware).
     */
    public StatDescriptor(@NotNull String id, @NotNull String displayName, @NotNull String symbol,
            @NotNull StatColor color, @NotNull StatCategory category, boolean percent) {
        this(id, displayName, symbol, color, category, percent, null);
    }

    /**
     * Creates a descriptor with a custom formatter. When {@code formatter} is
     * {@code null} the default percent-aware formatting is used.
     */
    public StatDescriptor(@NotNull String id, @NotNull String displayName, @NotNull String symbol,
            @NotNull StatColor color, @NotNull StatCategory category, boolean percent,
            BiFunction<Double, Boolean, String> formatter) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.color = color;
        this.category = category;
        this.percent = percent;
        this.formatter = formatter != null ? formatter : this::formatDefault;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public StatColor getColor() {
        return color;
    }

    public StatCategory getCategory() {
        return category;
    }

    /**
     * True when the stat value is displayed as a percentage (value * 100).
     */
    public boolean isPercent() {
        return percent;
    }

    public String getFormattedName() {
        return symbol + " " + displayName;
    }

    public String formatValue(double value, boolean showPlus) {
        return formatter.apply(value, showPlus);
    }

    /**
     * Formats the value with its color metadata. Note: this returns the plain
     * formatted value without any chat color codes; convert the descriptor's
     * {@link StatColor} with the platform color adapter to apply colors.
     */
    public String formatColoredValue(double value, boolean showPlus) {
        return formatValue(value, showPlus);
    }

    public String getHexColor() {
        return color.toHex();
    }

    private String formatDefault(double value, boolean showPlus) {
        String prefix = showPlus ? (value > 0 ? "+" : (value < 0 ? "-" : "")) : "";
        double display = showPlus ? Math.abs(value) : value;

        String formattedValue;
        if (percent) {
            formattedValue = String.format("%.1f%%", display * 100);
        } else if (display == (long) display) {
            formattedValue = String.format("%d", (long) display);
        } else {
            formattedValue = String.format("%.1f", display);
        }

        return symbol + " " + prefix + formattedValue + " " + displayName;
    }

    public enum StatCategory {
        RESOURCE, // health, mana (has current/max, ticks)
        ATTRIBUTE // damage, speed (pure derived values)
    }
}