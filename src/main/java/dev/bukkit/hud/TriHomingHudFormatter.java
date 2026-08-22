package dev.bukkit.hud;

import org.bukkit.ChatColor;

/**
 * Formatter for Trinity bow HUD. Loaded from HudConfig tri section.
 */
public final class TriHomingHudFormatter {

    private static volatile String READY = "&5Trinity Bow &8| &dHoming ready &7(3 &8× &7homing)";
    private static volatile String VOLLEY_READY = "&5Trinity &8| &dVolley ready";
    private static volatile String VOLLEY_CD = "&cVolley &7%cds";

    private TriHomingHudFormatter() {}

    public static void load(HudConfig cfg) {
        if (cfg == null || cfg.triFormats() == null) return;
        var f = cfg.triFormats();
        if (f.ready() != null) READY = f.ready();
        if (f.volleyReady() != null) VOLLEY_READY = f.volleyReady();
        if (f.volleyCd() != null) VOLLEY_CD = f.volleyCd();
    }

    public static String formatReady() {
        return ChatColor.translateAlternateColorCodes('&', READY);
    }

    public static String formatVolleyReady() {
        return ChatColor.translateAlternateColorCodes('&', VOLLEY_READY);
    }

    public static String formatVolleyCd(long millis) {
        String sec = String.format("%.1f", millis/1000.0);
        return ChatColor.translateAlternateColorCodes('&', VOLLEY_CD.replace("%cds", sec));
    }
}
