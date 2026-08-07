package dev.core.stat.descriptor;

import java.util.function.BiFunction;
import org.jetbrains.annotations.NotNull;
import dev.core.item.display.TextColor;

/**
 * Describes a stat with its metadata: display name, symbol, color, and
 * formatting logic. This replaces direct usage of StatType enum for
 * extensibility.
 */
public class StatDescriptor {

    private final String id;
    private final String displayName;
    private final String symbol;
    private final TextColor color;
    private final StatCategory category;
    private final BiFunction<Double, Boolean, String> formatter; // (value, showPlus) -> formatted string

    public StatDescriptor(@NotNull String id, @NotNull String displayName, @NotNull String symbol,
            @NotNull TextColor color, @NotNull StatCategory category,
            @NotNull BiFunction<Double, Boolean, String> formatter) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.color = color;
        this.category = category;
        this.formatter = formatter;
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

    public TextColor getColor() {
        return color;
    }

    public StatCategory getCategory() {
        return category;
    }

    public String getFormattedName() {
        return symbol + " " + displayName;
    }

    public String formatValue(double value, boolean showPlus) {
        return formatter.apply(value, showPlus);
    }

    public String formatColoredValue(double value, boolean showPlus) {
        return color.name() + " " + formatValue(value, showPlus);
    }

    public String getHexColor() {
        return color.toHex();
    }

    public enum StatCategory {
        RESOURCE, // health, mana (has current/max, ticks)
        ATTRIBUTE // damage, speed (pure derived values)
    }
}
