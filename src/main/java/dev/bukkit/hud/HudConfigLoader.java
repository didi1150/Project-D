package dev.bukkit.hud;

import org.bukkit.Color;

import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

/**
 * Loads hud.yml into HudConfig. All keys optional — absent entries fall back to
 * defaults. Supports nested background {alpha,r,g,b} and legacy
 * display.background-color "#AARRGGBB".
 */
public final class HudConfigLoader {

    private HudConfigLoader() {}

    public static HudConfig load(ConfigProvider provider) {
        if (provider == null) return HudConfig.defaults();
        ConfigSection root = provider.getRoot();
        if (root == null) return HudConfig.defaults();

        boolean enabled = root.getBoolean("enabled", HudConfig.DEFAULT_ENABLED);
        int maxKeys = root.getInt("max-keys", HudConfig.DEFAULT_MAX_KEYS);

        ConfigSection disp = root.getSection("display");
        // disp is auto-created by BukkitConfigSection, but treat null as defaults
        double distance = getDouble(disp, "distance", HudConfig.DEFAULT_DISTANCE);
        double vOff = getDouble(disp, "vertical-offset", HudConfig.DEFAULT_VERTICAL_OFFSET);
        double spacing = getDouble(disp, "spacing", HudConfig.DEFAULT_SPACING);
        float scale = (float) getDouble(disp, "scale", HudConfig.DEFAULT_SCALE);
        float viewRange = (float) getDouble(disp, "view-range", HudConfig.DEFAULT_VIEW_RANGE);
        int lineWidth = getInt(disp, "line-width", HudConfig.DEFAULT_LINE_WIDTH);
        int brightness = getInt(disp, "brightness", HudConfig.DEFAULT_BRIGHTNESS);
        int interp = getInt(disp, "interpolation-duration", HudConfig.DEFAULT_INTERP);
        int teleport = getInt(disp, "teleport-duration", HudConfig.DEFAULT_TELEPORT);
        boolean shadowed = getBool(disp, "shadowed", HudConfig.DEFAULT_SHADOWED);
        boolean seeThrough = getBool(disp, "see-through", HudConfig.DEFAULT_SEE_THROUGH);

        Color bg = loadBackground(disp);

        // formats.hunter
        ConfigSection hunterSec = root.getSection("formats.hunter");
        String bounceZero = null, bounce = null, shockArmed = null, shockPlain = null;
        if (hunterSec != null) {
            bounceZero = trim(hunterSec.getString("bounce-zero", null));
            bounce = trim(hunterSec.getString("bounce", null));
            shockArmed = trim(hunterSec.getString("shock-armed", null));
            shockPlain = trim(hunterSec.getString("shock-plain", null));
        }
        HudConfig.HunterFormats defaultsH = HudConfig.defaults().hunterFormats();
        HudConfig.HunterFormats formats = new HudConfig.HunterFormats(
                bounceZero != null ? bounceZero : defaultsH.bounceZero(),
                bounce != null ? bounce : defaultsH.bounce(),
                shockArmed != null ? shockArmed : defaultsH.shockArmed(),
                shockPlain != null ? shockPlain : defaultsH.shockPlain());

        ConfigSection msgs = root.getSection("messages");
        String notEnough = null;
        if (msgs != null) notEnough = trim(msgs.getString("not-enough", null));
        if (notEnough == null) notEnough = HudConfig.defaults().messages().notEnough();
        HudConfig.Messages messages = new HudConfig.Messages(notEnough);

        return new HudConfig(enabled, maxKeys, distance, vOff, spacing, scale, viewRange, lineWidth, bg,
                brightness, interp, teleport, shadowed, seeThrough, formats, messages);
    }

    private static Color loadBackground(ConfigSection disp) {
        if (disp == null) return HudConfig.DEFAULT_BG_COLOR;
        ConfigSection bgSec = null;
        try { bgSec = disp.getSection("background"); } catch (Exception ignored) {}
        // nested form has priority
        if (bgSec != null && hasAny(bgSec, "alpha", "r", "g", "b")) {
            int a = getInt(bgSec, "alpha", 96);
            int r = getInt(bgSec, "r", 0);
            int g = getInt(bgSec, "g", 0);
            int b = getInt(bgSec, "b", 0);
            a = clamp(a, 0, 255, 96);
            r = clamp(r, 0, 255, 0);
            g = clamp(g, 0, 255, 0);
            b = clamp(b, 0, 255, 0);
            return Color.fromARGB(a, r, g, b);
        }
        // legacy hex fallback
        String legacy = null;
        try { legacy = trim(disp.getString("background-color", null)); } catch (Exception ignored) {}
        if (legacy == null) legacy = trim(disp.getString("background_color", null));
        if (legacy != null) {
            Color c = parseHexColor(legacy);
            if (c != null) return c;
        }
        return HudConfig.DEFAULT_BG_COLOR;
    }

    private static boolean hasAny(ConfigSection sec, String... keys) {
        if (sec == null) return false;
        for (String k : keys) {
            for (String existing : sec.getKeys()) if (existing.equalsIgnoreCase(k)) return true;
        }
        try { return sec.getString(keys[0], null) != null; } catch (Exception e) { return false; }
    }

    private static Color parseHexColor(String raw) {
        if (raw == null) return null;
        raw = raw.trim().replace("0x", "#");
        if (raw.startsWith("#")) raw = raw.substring(1);
        try {
            if (raw.length() == 8) { // AARRGGBB
                long v = Long.parseLong(raw, 16);
                int a = (int) ((v >> 24) & 0xFF);
                int r = (int) ((v >> 16) & 0xFF);
                int g = (int) ((v >> 8) & 0xFF);
                int b = (int) (v & 0xFF);
                return Color.fromARGB(a, r, g, b);
            } else if (raw.length() == 6) { // RRGGBB -> opaque
                int r = Integer.parseInt(raw.substring(0, 2), 16);
                int g = Integer.parseInt(raw.substring(2, 4), 16);
                int b = Integer.parseInt(raw.substring(4, 6), 16);
                return Color.fromARGB(255, r, g, b);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static int clamp(int v, int min, int max, int def) {
        if (v < min || v > max) return Math.max(min, Math.min(max, v));
        return v;
    }

    private static String trim(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
    private static double getDouble(ConfigSection sec, String path, double def) {
        if (sec == null) return def;
        try { return sec.getDouble(path, def); } catch (Exception e) { return def; }
    }
    private static int getInt(ConfigSection sec, String path, int def) {
        if (sec == null) return def;
        try { return sec.getInt(path, def); } catch (Exception e) { return def; }
    }
    private static boolean getBool(ConfigSection sec, String path, boolean def) {
        if (sec == null) return def;
        try { return sec.getBoolean(path, def); } catch (Exception e) { return def; }
    }
}
