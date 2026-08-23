package dev.bukkit.hud;

import org.bukkit.ChatColor;

import dev.core.utils.ColorCodes;

/**
 * Formats hunter bow state into HUD lines.
 * Two stacked displays:
 *  - bounce: "Hunter's Bow | Bouncy ●●○ (2/3)"
 *  - shock:  "◉ Shock armed" / "○ No shock"
 */
public final class HunterHudFormatter {

    private static final int MAX_BOUNCES = 3;

    private static volatile HudConfig.HunterFormats cached = HudConfig.defaults().hunterFormats();

    private HunterHudFormatter() {}

    public static void load(HudConfig cfg) {
        if (cfg != null && cfg.hunterFormats() != null) cached = cfg.hunterFormats();
    }

    public static String formatBounce(int bounces) {
        String dots = dots(bounces);
        HudConfig.HunterFormats fmt = cached;
        String template;
        if (bounces == 0) {
            template = fmt.bounceZero();
        } else {
            template = fmt.bounce();
        }
        // placeholders %dots% and %bounces% ; allow & codes, hex and rgb() in config
        String out = template.replace("%dots%", dots).replace("%bounces%", String.valueOf(bounces));
        return ColorCodes.translate(out);
    }

    public static String formatShock(boolean armed) {
        HudConfig.HunterFormats fmt = cached;
        String template = armed ? fmt.shockArmed() : fmt.shockPlain();
        return ColorCodes.translate(template);
    }

    /** Short form for shot summary (optional transient, not used for persistent held). */
    public static String formatShot(int bounces, boolean explosive) {
        StringBuilder sb = new StringBuilder(ChatColor.GOLD + "Hunter's Bow " + ChatColor.DARK_GRAY + "| ");
        if (bounces > 0) {
            sb.append(ChatColor.AQUA).append("Recon ×").append(bounces).append(" ");
        } else {
            sb.append(ChatColor.GRAY).append("Plain ");
        }
        if (explosive) {
            sb.append(ChatColor.RED).append("◉ Shock");
        } else {
            sb.append(ChatColor.DARK_GRAY).append("○ No shock");
        }
        return sb.toString();
    }

    private static String dots(int bounces) {
        String filled = ChatColor.AQUA + "●";
        String empty = ChatColor.GRAY + "○";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_BOUNCES; i++) {
            sb.append(i < bounces ? filled : empty);
        }
        return sb.toString();
    }
}
