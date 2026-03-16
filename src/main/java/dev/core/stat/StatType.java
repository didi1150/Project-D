package dev.core.stat;

import dev.core.item.display.TextColor;

public enum StatType {

    // Combat Stats
    ATTACK_DAMAGE("Attack Damage", "⚔", TextColor.GOLD), ABILITY_POWER("Ability Power", "✦", TextColor.BLUE),
    ARMOR("Armor", "🛡", TextColor.GRAY), MAGIC_RESIST("Magic Resist", "◈", TextColor.DARK_PURPLE),

    // Speed and Combat Mechanics
    ATTACK_SPEED("Attack Speed", "⚡", TextColor.YELLOW), MOVE_SPEED("Movement Speed", "➤", TextColor.DARK_AQUA),
    ABILITY_HASTE("Ability Haste", "⟡", TextColor.LIGHT_PURPLE),

    // Penetration Stats
    LETHALITY("Lethality", "⚈", TextColor.DARK_GRAY), ARMOR_PENETRATION("Armor Penetration", "⚆", TextColor.DARK_GRAY),

    // Critical and Support
    CRIT_CHANCE("Critical Chance", "⚹", TextColor.GOLD),
    HEAL_AND_SHIELD_POWER("Heal & Shield Power", "♥", TextColor.GREEN),

    // Health Stats
    HEALTH_MAX("Max Health", "❤", TextColor.RED), HEALTH_RESOURCE("Health", "❤", TextColor.RED),
    HEALTH_REGEN("Health Regeneration", "♡", TextColor.GREEN),

    // Mana Stats
    MANA_MAX("Max Mana", "✦", TextColor.BLUE), MANA_RESOURCE("Mana", "✦", TextColor.BLUE),
    MANA_REGEN("Mana Regeneration", "◊", TextColor.LIGHT_PURPLE);

    private final String displayName;
    private final String symbol;
    private final TextColor color;

    StatType(String displayName, String symbol, TextColor color) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.color = color;
    }

    /**
     * Get the human-readable display name of this stat
     * 
     * @return The display name (e.g., "Attack Damage")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the symbol representing this stat
     * 
     * @return The symbol (e.g., "⚔" for Attack Damage)
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Get the text color for this stat
     * 
     * @return The TextColor enum value
     */
    public TextColor getColor() {
        return color;
    }

    /**
     * Get a formatted string with symbol and display name
     * 
     * @return Formatted string like "⚔ Attack Damage"
     */
    public String getFormattedName() {
        return symbol + " " + displayName;
    }

    /**
     * Get the hex color code for this stat
     * 
     * @return Hex color string
     */
    public String getHexColor() {
        return color.toHex();
    }

    /**
     * Create a stat display string with value
     * 
     * @param value    The stat value to display
     * @param showPlus Whether to show + for positive values
     * @return Formatted stat string like "⚔ +15 Attack Damage"
     */
    public String formatValue(double value, boolean showPlus) {
        String prefix = (showPlus) ? (value > 0 ? "+" : "-") : "";

        // Format value based on stat type
        String formattedValue;
        if (this == CRIT_CHANCE || this == ATTACK_SPEED) {
            // Percentage stats
            formattedValue = String.format("%.1f%%", this == CRIT_CHANCE ? value : value * 100);
        } else if (value == (long) value) {
            // Integer values
            formattedValue = String.format("%d", (long) value);
        } else {
            // Decimal values
            formattedValue = String.format("%.1f", value);
        }

        return symbol + " " + prefix + formattedValue + " " + displayName;
    }

    /**
     * Create a colored stat display string with value
     * 
     * @param value    The stat value to display
     * @param showPlus Whether to show + for positive values
     * @return Colored formatted stat string
     */
    public String formatColoredValue(double value, boolean showPlus) {
        return color.name() + " " + formatValue(value, showPlus);
    }

}
