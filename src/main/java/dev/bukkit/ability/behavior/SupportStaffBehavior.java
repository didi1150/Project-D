package dev.bukkit.ability.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.StaffHudFormatter;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.CombatRelation;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.EntityManager;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;
import fr.skytasul.glowingentities.GlowingEntities;

/**
 * Per-holder behavior for the Utility Staff. Owns every piece of the staff
 * runtime that runs while the support merely holds the item: the mode state,
 * the per-player glowing entity indicators for Modes 1 &amp; 2, the particle
 * ring for Mode 3's pushback radius, and the persistent HUD overlay showing the
 * current mode.
 *
 * <p>
 * Two abilities ({@code STAFF_USE} and {@code STAFF_TOGGLE}) share the same
 * behavior instance per holder via {@link #HOLDER_CACHE}. A per-holder
 * TickEvent subscription handles all continuous visuals.
 * </p>
 *
 * <p>
 * When the staff is unequipped or the holder dies/quits, indicators are cleared
 * and the HUD line is hidden.
 * </p>
 */
public class SupportStaffBehavior implements AbilityBehavior {

    /** Item id the staff behavior binds to (see items.yml). */
    public static final String ITEM_ID = "UTILITY_STAFF";

    // ---- Targeting parameters -------------------------------------------------
    /** Maximum raycast range (blocks) for ally targeting. */
    private static final double TARGETING_RANGE = 15.0;
    /** Ray trace size (blocks) for entity hit detection. */
    private static final double RAY_SIZE = 0.5;

    // ---- Mode 3 pushback ring -------------------------------------------------
    /** Radius of the pushback indicator ring (blocks). */
    private static final double PUSH_RADIUS = 8.0;
    /** Number of particle points in the ring. */
    private static final int RING_POINTS = 40;

    // ---- State per holder -----------------------------------------------------
    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> HOLDER_REFCNT = new ConcurrentHashMap<>();
    private static final Map<UUID, SupportStaffBehavior> HOLDER_CACHE = new ConcurrentHashMap<>();

    private ActiveAbility ctx;

    public SupportStaffBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
        UUID uuid = ctx.getHolder().getUuid();
        HOLDER_CACHE.put(uuid, this);
        HOLDER_REFCNT.merge(uuid, 1, Integer::sum);
        STATES.computeIfAbsent(uuid, k -> new PlayerState());
    }

    public static SupportStaffBehavior forHolder(UUID uuid) {
        return HOLDER_CACHE.get(uuid);
    }

    /**
     * Returns the current mode (1=Heal, 2=Shield, 3=Pushback) for the given holder.
     */
    public int getCurrentMode() {
        PlayerState state = STATES.get(ctx.getHolder().getUuid());
        return state != null ? state.currentMode : 1;
    }

    /** Cycles the mode: 1→2→3→1. */
    public void cycleMode() {
        PlayerState state = STATES.get(ctx.getHolder().getUuid());
        if (state != null) {
            state.currentMode = state.currentMode >= 3 ? 1 : state.currentMode + 1;
            // Clear any active glow indicator when switching modes so the
            // colour is refreshed immediately on the next tick.
            state.clearGlowingTarget();
        }
    }

    // ---- Lifecycle ------------------------------------------------------------

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        UUID uuid = ctx.getHolder().getUuid();
        HOLDER_CACHE.put(uuid, this);
        STATES.computeIfAbsent(uuid, k -> new PlayerState());
        boolean isFirst = HOLDER_REFCNT.getOrDefault(uuid, 0) == 1;
        if (isFirst) {
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onTick, TickEvent.class));
        }
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        UUID uuid = ctx.getHolder().getUuid();
        int cnt = HOLDER_REFCNT.getOrDefault(uuid, 1) - 1;
        if (cnt > 0) {
            HOLDER_REFCNT.put(uuid, cnt);
            return;
        }
        HOLDER_REFCNT.remove(uuid);
        HOLDER_CACHE.remove(uuid, this);

        // Clean up visuals
        Player player = Bukkit.getPlayer(uuid);
        PlayerState state = STATES.remove(uuid);
        if (state != null) {
            if (player != null) {
                state.clearGlowingTarget();
                hideHudKeys(player);
            }
        }
        ctx.getSubscriptions().unsubscribeAll();
    }

    // ---- Per-tick handler -----------------------------------------------------

    private void onTick(TickEvent e) {
        UUID uuid = ctx.getHolder().getUuid();
        PlayerState state = STATES.get(uuid);
        if (state == null) {
            if (!HOLDER_REFCNT.containsKey(uuid))
                return;
            state = STATES.computeIfAbsent(uuid, k -> new PlayerState());
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || player.isDead() || !player.isOnline()) {
            if (state.hasActiveGlow()) {
                state.clearGlowingTarget();
            }
            return;
        }

        if (!holdsStaff(player)) {
            if (state.hasActiveGlow()) {
                state.clearGlowingTarget();
            }
            hideHudKeys(player);
            return;
        }

        // Persistent HUD line showing current mode
        HudOverlayService hud = HudOverlayService.getInstance();
        hud.show(player, "staff:mode", StaffHudFormatter.formatMode(state.currentMode), 0, 10);

        // [DEBUG] Log tick info every 2 seconds
        if (System.currentTimeMillis() % 2000 < 50) {
            GlowingEntities glow = DMain.getInstance().getGlowingEntities();
            System.out.println("[StaffDebug] tick mode=" + state.currentMode + " holdStaff=true glowLib="
                    + (glow != null) + " onlinePlayers=" + Bukkit.getOnlinePlayers().size());
        }

        // Mode-specific visual indicators
        switch (state.currentMode) {
        case 1, 2 -> tickTargetingIndicator(player, state);
        case 3 -> tickPushbackIndicator(player, state);
        }
    }

    // ---- Mode 1/2 targeting indicator -----------------------------------------

    private void tickTargetingIndicator(Player player, PlayerState state) {
        Entity target = raycastAlly(player);
        Entity currentGlowTarget = state.lastGlowingTarget;

        if (target != null && !target.equals(currentGlowTarget)) {
            // Target changed: clear old, set new
            state.clearGlowingTarget();
            try {
                GlowingEntities glow = DMain.getInstance().getGlowingEntities();
                if (glow != null) {
                    ChatColor color = state.currentMode == 1 ? ChatColor.GREEN : ChatColor.YELLOW;
                    glow.setGlowing(target, player, color);
                    state.lastGlowingTarget = target;
//                    System.out.println("[StaffDebug] GLOW SET on " + target.getName() + " color=" + color.name()
//                            + " mode=" + state.currentMode);
                } else {
//                    System.out.println("[StaffDebug] GLOW FAILED: GlowingEntities is null");
                }
            } catch (Exception ex) {
//                System.out.println("[StaffDebug] GLOW EXCEPTION: " + ex.getMessage());
            }
        } else if (target == null && currentGlowTarget != null) {
            // No target anymore: clear glow
//            System.out.println("[StaffDebug] GLOW CLEARED: target lost");
            state.clearGlowingTarget();
        }
    }

    // ---- Mode 3 pushback radius indicator -------------------------------------

    private void tickPushbackIndicator(Player player, PlayerState state) {
        // Clear any lingering glow from modes 1/2
        if (state.hasActiveGlow()) {
            state.clearGlowingTarget();
        }

        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.15, 0);
        double angleOffset = Math.toRadians((System.currentTimeMillis() / 50) % 360);
        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x55, 0xCC, 0xFF), 0.8f);
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = angleOffset + 2 * Math.PI * i / RING_POINTS;
            double x = center.getX() + PUSH_RADIUS * Math.cos(angle);
            double z = center.getZ() + PUSH_RADIUS * Math.sin(angle);
            Location ringPoint = center.clone();
            ringPoint.setX(x);
            ringPoint.setZ(z);
            world.spawnParticle(Particle.DUST, ringPoint, 1, 0, 0, 0, 0, dust);
        }
    }

    // ---- Raycast helpers ------------------------------------------------------

    /**
     * Raycasts from the player's eye in the look direction to find the nearest
     * allied living entity. Returns the Bukkit Entity or null.
     */
    private Entity raycastAlly(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        // [DEBUG] Count nearby entities
//        int nearbyCount = 0;
//        for (Entity e : world.getNearbyEntities(eye, TARGETING_RANGE, TARGETING_RANGE, TARGETING_RANGE)) {
//            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
//                nearbyCount++;
//            }
//        }

        RayTraceResult result = world.rayTrace(eye.clone().add(dir), dir, TARGETING_RANGE, FluidCollisionMode.NEVER,
                true, RAY_SIZE, this::isAlliedTarget);
        if (result == null || result.getHitEntity() == null) {
//            if (System.currentTimeMillis() % 2000 < 50) {
//                System.out.println("[StaffDebug] raycast miss: nearbyLiving=" + nearbyCount + " eye="
//                        + String.format("%.1f,%.1f,%.1f", eye.getX(), eye.getY(), eye.getZ()) + " dir="
//                        + String.format("%.3f,%.3f,%.3f", dir.getX(), dir.getY(), dir.getZ()));
//            }
            return null;
        }

        Entity hit = result.getHitEntity();
        if (!(hit instanceof LivingEntity living)) {
//            System.out.println("[StaffDebug] raycast hit non-living: " + hit.getType());
            return null;
        }
        if (living.isDead() || !living.isValid()) {
//            System.out.println("[StaffDebug] raycast hit dead/invalid: " + hit.getName());
            return null;
        }
        if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
//            System.out.println("[StaffDebug] raycast hit ghost: " + hit.getName());
            return null;
        }

//        System.out.println("[StaffDebug] raycast HIT: " + hit.getName() + " dist="
//                + String.format("%.1f", eye.distance(hit.getLocation())));
        return hit;
    }

    /**
     * Entity filter for the ray trace: accepts living entities on the player team.
     */
    private boolean isAlliedTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living))
            return false;
        if (living.isDead() || !living.isValid())
            return false;
        if (EntityManager.getInstance().isGhost(living.getUniqueId()))
            return false;
        boolean allied = CombatRelation.isPlayerTeam(entity);
//        if (System.currentTimeMillis() % 5000 < 50) {
//            System.out.println("[StaffDebug] isAlliedTarget: " + entity.getName() + " type=" + entity.getType()
//                    + " alive=" + living.isValid() + " playerTeam=" + allied);
//        }
        return allied;
    }

    // ---- HUD helpers ----------------------------------------------------------

    private static void hideHudKeys(Player p) {
        HudOverlayService.getInstance().hide(p, "staff:mode");
    }

    // ---- Item check -----------------------------------------------------------

    private boolean holdsStaff(Player player) {
        return ITEM_ID.equals(BukkitItemStackAdapter.getRpgItemId(player.getInventory().getItemInMainHand()));
    }

    // ---- Per-holder state -----------------------------------------------------

    static final class PlayerState {
        int currentMode = 1;
        Entity lastGlowingTarget = null;

        boolean hasActiveGlow() {
            return lastGlowingTarget != null && lastGlowingTarget.isValid();
        }

        void clearGlowingTarget() {
            if (lastGlowingTarget == null)
                return;
            try {
                Player player = findPlayer();
                if (player != null) {
                    GlowingEntities glow = DMain.getInstance().getGlowingEntities();
                    if (glow != null) {
                        glow.unsetGlowing(lastGlowingTarget, player);
                    }
                }
            } catch (Exception ignored) {
            }
            lastGlowingTarget = null;
        }

        private Player findPlayer() {
            if (lastGlowingTarget == null)
                return null;
            // The glow was set per-player; we need the owning player.
            // Walk all online players to find who has this behavior.
            for (Player p : Bukkit.getOnlinePlayers()) {
                SupportStaffBehavior behavior = HOLDER_CACHE.get(p.getUniqueId());
                if (behavior != null) {
                    return p;
                }
            }
            return null;
        }
    }
}
