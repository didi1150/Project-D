package dev.bukkit.game.boss.stalactite;

import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * Tunable constants for the stalactite system. Values match the design spec:
 * default 10% push, 10% max-health explosion, 20s regrow, BlockDisplay dripstone.
 * The GROW boss stage can override the push and explosion percentages.
 */
public final class StalactiteConfig {

    public static final double MAX_HEALTH = 80.0;

    /** Default boss push as percentage points of full ascent. */
    public static final double PUSH_AMOUNT_PERCENT = 10.0;

    /** Default ground-explosion damage as a percentage of the victim's max health. */
    public static final double EXPLOSION_PERCENT = 10.0;

    /** Ground-explosion radius in blocks (sphere). */
    public static final double EXPLOSION_RADIUS = 10.0;

    /** Extra tolerance when checking the falling stalactite against a boss hitbox. */
    public static final double BOSS_COLLISION_MARGIN = 0.35;

    /** Maximum time a stalactite may keep falling before it is removed. */
    public static final int MAX_FALL_TICKS = 15 * 20;

    /** fixed regrow time once shattered — spec: 20s */
    public static final int REGROW_TICKS = 400;

    /** ceiling offset when spawning debug stalactite above player */
    public static final double CEILING_OFFSET_Y = 8.0;

    /** visual */
    public static final Material DISPLAY_MATERIAL = Material.POINTED_DRIPSTONE;

    /** interaction hitbox dims (also the max stalk thickness/height before fall) */
    public static final float HITBOX_WIDTH = 6.0f;
    public static final float HITBOX_HEIGHT = 10.0f;

    /**
     * Max dimensions of the stalactite BlockDisplay scale. Adjustable constants —
     * changing these changes the fully-grown size. X/Z are thickness, Y is length
     * hanging from ceiling. The display is rendered with a negative Y scale to
     * point the pointed_dripstone downward (tip at bottom).
     */
    public static final float MAX_DISPLAY_SCALE_X = 6.0f;
    public static final float MAX_DISPLAY_SCALE_Y = 10.0f;
    public static final float MAX_DISPLAY_SCALE_Z = 6.0f;

    /**
     * Max BlockDisplay scale as a Vector3f (positive — negated on Y when applied).
     */
    public static final Vector3f MAX_DISPLAY_SCALE = new Vector3f(MAX_DISPLAY_SCALE_X, MAX_DISPLAY_SCALE_Y,
            MAX_DISPLAY_SCALE_Z);

    /** Max BlockDisplay scale pointed downward (negative Y). */
    public static Vector3f maxDisplayScaleDownward() {
        return new Vector3f(MAX_DISPLAY_SCALE_X, -MAX_DISPLAY_SCALE_Y, MAX_DISPLAY_SCALE_Z);
    }

    /**
     * Shrunk/hidden scale used while regrowing (still downward-pointing) — starts
     * from 0 on all axes.
     */
    public static final Vector3f HIDDEN_SCALE_DOWNWARD = new Vector3f(0.0f, -0.0f, 0.0f);

    /**
     * Tapered hitbox: stalactite width narrows from base (ceiling) to tip. Base
     * width is MAX_DISPLAY_SCALE_X/Z; tip width is this value. Used for
     * projectile/melee hit detection so the hitbox matches the visual taper.
     */
    public static final float HITBOX_TIP_WIDTH = 0.5f;
    public static final float HITBOX_TIP_HALF_X = HITBOX_TIP_WIDTH / 2.0f;
    public static final float HITBOX_TIP_HALF_Z = HITBOX_TIP_WIDTH / 2.0f;

    /**
     * Projectile collider: a larger, different entity that encloses the full
     * stalactite length. A Slime with this size centered on the stalactite covers
     * the entire 5-block height.
     */
    public static final int COLLIDER_SLIME_SIZE = 10;

    // ---- Hitbox highlight (debug/visual aid) ----
    /** Enable transparent green glass highlights for each hitbox. */
    public static final boolean HITBOX_HIGHLIGHT_ENABLED = true;

    /** Material for the rectangular Interaction hitbox highlight. */
    public static final Material HITBOX_HIGHLIGHT_MATERIAL = Material.LIME_STAINED_GLASS;

    /** Material for the tapered frustum segments (shows narrowing to tip). */
    public static final Material HITBOX_HIGHLIGHT_TAPER_MATERIAL = Material.GREEN_STAINED_GLASS;

    /** Number of glass segments used to visualize the tapered hitbox. */
    public static final int HITBOX_HIGHLIGHT_TAPER_SEGMENTS = 4;

    /**
     * BlockDisplay brightness for highlights so glass is visible even in dark
     * caves. Uses max block/sky light (15,15).
     */
    public static final int HITBOX_HIGHLIGHT_BRIGHTNESS = 15;

    /**
     * Projectile tracker: radius (blocks) around each intact stalactite to scan for
     * mid-air projectiles. Interaction hitboxes are passable so arrows would
     * otherwise only collide when a solid block is nearby. The per-tick tracker
     * ensures reliable hits even in open air.
     */
    public static final double PROJECTILE_TRACK_RADIUS = 2.0;

    /** Extra margin added to the hitbox bounding-box check for fast projectiles. */
    public static final double PROJECTILE_TRACK_MARGIN = 0.45;

    /** physics */
    public static final double FALL_GRAVITY_PER_TICK = 0.09;
    public static final double FALL_INITIAL_VELOCITY = 0.0;

    private StalactiteConfig() {
    }
}
