package dev.core.status;

/**
 * Registry of all crowd-control (and CC-immunity) status effects. Each type
 * carries its display metadata and its {@link CcCategory}, which drives the
 * stacking rules in {@link StatusEffectManager}.
 */
public enum StatusEffectType {

	SLOWED("❄", "Slowed", "§b", CcCategory.SOFT, true),
	ROOTED("⛓", "Rooted", "§d", CcCategory.SOFT, true),
	STUNNED("⚡", "Stunned", "§e", CcCategory.HARD, false),
	AIRBORNE("↑", "Airborne", "§7", CcCategory.HARD, false),
	CC_IMMUNE("✦", "CC Immune", "§6", CcCategory.IMMUNITY, false),
	WITHER("☠", "Wither", "§8", CcCategory.DEBUFF, true),
	ABSORPTION("♥", "Absorption", "§e", CcCategory.BUFF, false);

	private final String icon;
	private final String displayName;
	private final String color; // legacy §-code, e.g. "§b"
	private final CcCategory category;
	private final boolean fadeOutByDefault;

	StatusEffectType(String icon, String displayName, String color, CcCategory category, boolean fadeOutByDefault) {
		this.icon = icon;
		this.displayName = displayName;
		this.color = color;
		this.category = category;
		this.fadeOutByDefault = fadeOutByDefault;
	}

	public String getIcon() {
		return icon;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getColor() {
		return color;
	}

	public CcCategory getCategory() {
		return category;
	}

	public boolean isFadeOutByDefault() {
		return fadeOutByDefault;
	}
}