package dev.bukkit.hud;

import dev.core.utils.ColorCodes;

/**
 * Formatter for Utility Staff HUD. Loaded from HudConfig staff section.
 * Displays the current staff mode (Mending Touch / Aegis Ward / Tempest Gust)
 * as a persistent TextDisplay while the staff is held.
 */
public final class StaffHudFormatter {

	private static volatile String MODE1 = "&2Utility Staff &8| &aMending Touch";
	private static volatile String MODE2 = "&2Utility Staff &8| &eAegis Ward";
	private static volatile String MODE3 = "&2Utility Staff &8| &bTempest Gust";

	private StaffHudFormatter() {
	}

	public static void load(HudConfig cfg) {
		if (cfg == null || cfg.staffFormats() == null)
			return;
		var f = cfg.staffFormats();
		if (f.mode1() != null)
			MODE1 = f.mode1();
		if (f.mode2() != null)
			MODE2 = f.mode2();
		if (f.mode3() != null)
			MODE3 = f.mode3();
	}

	/**
	 * Formats the current staff mode into a HUD display line.
	 *
	 * @param mode 1 = Mending Touch, 2 = Aegis Ward, 3 = Tempest Gust
	 * @return legacy-colored string ready for the HUD overlay
	 */
	public static String formatMode(int mode) {
		String template;
		switch (mode) {
			case 1 -> template = MODE1;
			case 2 -> template = MODE2;
			case 3 -> template = MODE3;
			default -> template = MODE1;
		}
		return ColorCodes.translate(template);
	}
}
