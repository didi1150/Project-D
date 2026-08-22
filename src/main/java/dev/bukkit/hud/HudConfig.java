package dev.bukkit.hud;

import org.bukkit.Color;

/**
 * Immutable HUD configuration loaded from hud.yml. Defaults match the previous
 * hard-coded constants in HudOverlayService so an absent file is safe.
 */
public final class HudConfig {

    // defaults mirroring HudOverlayService former constants
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MAX_KEYS = 4;
    public static final double DEFAULT_DISTANCE = 1.85;
    public static final double DEFAULT_VERTICAL_OFFSET = -0.35;
    public static final double DEFAULT_SPACING = 0.28;
    public static final float DEFAULT_SCALE = 0.65f;
    public static final float DEFAULT_VIEW_RANGE = 0.9f;
    public static final int DEFAULT_LINE_WIDTH = 300;
    public static final Color DEFAULT_BG_COLOR = Color.fromARGB(96, 0, 0, 0);
    public static final int DEFAULT_BRIGHTNESS = 15;
    public static final int DEFAULT_INTERP = 1;
    public static final int DEFAULT_TELEPORT = 0;
    public static final boolean DEFAULT_SHADOWED = true;
    public static final boolean DEFAULT_SEE_THROUGH = false;

    private final boolean enabled;
    private final int maxKeys;
    private final double distance;
    private final double verticalOffset;
    private final double spacing;
    private final float scale;
    private final float viewRange;
    private final int lineWidth;
    private final Color bgColor;
    private final int brightness;
    private final int interpolationDuration;
    private final int teleportDuration;
    private final boolean shadowed;
    private final boolean seeThrough;
    private final HunterFormats hunterFormats;
    private final TriFormats triFormats;
    private final Messages messages;

    public record HunterFormats(String bounceZero, String bounce, String shockArmed, String shockPlain) {}
    public record TriFormats(String ready, String volleyReady, String volleyCd) {}
    public record Messages(String notEnough) {}

    public HudConfig(boolean enabled, int maxKeys, double distance, double verticalOffset, double spacing,
                     float scale, float viewRange, int lineWidth, Color bgColor, int brightness,
                     int interpolationDuration, int teleportDuration, boolean shadowed, boolean seeThrough,
                     HunterFormats hunterFormats, TriFormats triFormats, Messages messages) {
        this.enabled = enabled;
        this.maxKeys = clampInt(maxKeys, 1, 8, DEFAULT_MAX_KEYS);
        this.distance = clamp(distance, 0.4, 5.0, DEFAULT_DISTANCE);
        this.verticalOffset = clamp(verticalOffset, -2.0, 2.0, DEFAULT_VERTICAL_OFFSET);
        this.spacing = clamp(spacing, 0.1, 1.0, DEFAULT_SPACING);
        this.scale = (float) clamp(scale, 0.2, 2.0, DEFAULT_SCALE);
        this.viewRange = (float) clamp(viewRange, 0.1, 2.5, DEFAULT_VIEW_RANGE);
        this.lineWidth = clampInt(lineWidth, 50, 500, DEFAULT_LINE_WIDTH);
        this.bgColor = bgColor != null ? bgColor : DEFAULT_BG_COLOR;
        this.brightness = clampInt(brightness, 0, 15, DEFAULT_BRIGHTNESS);
        this.interpolationDuration = clampInt(interpolationDuration, 0, 20, DEFAULT_INTERP);
        this.teleportDuration = clampInt(teleportDuration, 0, 20, DEFAULT_TELEPORT);
        this.shadowed = shadowed;
        this.seeThrough = seeThrough;
        this.hunterFormats = hunterFormats != null ? hunterFormats : defaultHunterFormats();
        this.triFormats = triFormats != null ? triFormats : defaultTriFormats();
        this.messages = messages != null ? messages : defaultMessages();
    }

    public static HudConfig defaults() {
        return new HudConfig(DEFAULT_ENABLED, DEFAULT_MAX_KEYS, DEFAULT_DISTANCE, DEFAULT_VERTICAL_OFFSET,
                DEFAULT_SPACING, DEFAULT_SCALE, DEFAULT_VIEW_RANGE, DEFAULT_LINE_WIDTH, DEFAULT_BG_COLOR,
                DEFAULT_BRIGHTNESS, DEFAULT_INTERP, DEFAULT_TELEPORT, DEFAULT_SHADOWED, DEFAULT_SEE_THROUGH,
                defaultHunterFormats(), defaultTriFormats(), defaultMessages());
    }

    private static HunterFormats defaultHunterFormats() {
        return new HunterFormats(
                "&6Hunter's Bow &8| &7Bouncy %dots% &8(0/3) &7— plain",
                "&6Hunter's Bow &8| &bBouncy %dots% &7(%bounces%/3)",
                "&c◉ Shock Bolt ARMED &7— next arrow detonates",
                "&7○ Shock — plain  &8○ No shock");
    }

    private static TriFormats defaultTriFormats() {
        return new TriFormats(
                "&5Trinity Bow &8| &dHoming ready &7(3 &8× &7homing)",
                "&5Trinity &8| &dVolley ready",
                "&cVolley &7%cds");
    }

    private static Messages defaultMessages() {
        return new Messages("&cNot enough <resource>!");
    }

    private static double clamp(double v, double min, double max, double def) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return def;
        return Math.max(min, Math.min(max, v));
    }
    private static int clampInt(int v, int min, int max, int def) {
        if (v < min || v > max) return Math.max(min, Math.min(max, v));
        return v;
    }

    public boolean enabled() { return enabled; }
    public int maxKeys() { return maxKeys; }
    public double distance() { return distance; }
    public double verticalOffset() { return verticalOffset; }
    public double spacing() { return spacing; }
    public float scale() { return scale; }
    public float viewRange() { return viewRange; }
    public int lineWidth() { return lineWidth; }
    public Color bgColor() { return bgColor; }
    public int brightness() { return brightness; }
    public int interpolationDuration() { return interpolationDuration; }
    public int teleportDuration() { return teleportDuration; }
    public boolean shadowed() { return shadowed; }
    public boolean seeThrough() { return seeThrough; }
    public HunterFormats hunterFormats() { return hunterFormats; }
    public TriFormats triFormats() { return triFormats; }
    public Messages messages() { return messages; }
}
