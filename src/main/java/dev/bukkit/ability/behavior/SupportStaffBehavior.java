package dev.bukkit.ability.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.format.NamedTextColor;

import dev.bukkit.DMain;
import dev.bukkit.glow.PlayerGlowService;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.StaffHudFormatter;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.StaffTargeting;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;

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
        STATES.computeIfAbsent(uuid, _ -> new PlayerState());
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
            state.clearGlowingTarget(Bukkit.getPlayer(ctx.getHolder().getUuid()));
        }
    }

    // ---- Lifecycle ------------------------------------------------------------

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        UUID uuid = ctx.getHolder().getUuid();
        HOLDER_CACHE.put(uuid, this);
        STATES.computeIfAbsent(uuid, _ -> new PlayerState());
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
                state.clearGlowingTarget(player);
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
            state = STATES.computeIfAbsent(uuid, _ -> new PlayerState());
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || player.isDead() || !player.isOnline()) {
            if (state.hasActiveGlow()) {
                state.clearGlowingTarget(null);
            }
            return;
        }

        if (!holdsStaff(player)) {
            if (state.hasActiveGlow()) {
                state.clearGlowingTarget(player);
            }
            hideHudKeys(player);
            return;
        }

        // Persistent HUD line showing current mode
        HudOverlayService hud = HudOverlayService.getInstance();
        hud.show(player, "staff:mode", StaffHudFormatter.formatMode(state.currentMode), 0, 10);

        // Mode-specific visual indicators
        switch (state.currentMode) {
        case 1, 2 -> tickTargetingIndicator(player, state);
        case 3 -> tickPushbackIndicator(player, state);
        }
    }

    // ---- Mode 1/2 targeting indicator -----------------------------------------

    private void tickTargetingIndicator(Player player, PlayerState state) {
        Entity target = StaffTargeting.raycastAlly(player);
        Entity currentGlowTarget = state.lastGlowingTarget;

        if (target != null) {
            if (!target.equals(currentGlowTarget)) {
                // Target changed: clear the old outline first.
                state.clearGlowingTarget(player);
                state.lastGlowingTarget = target;
            }
            // Re-assert every tick so the service can follow an entity that
            // changed world / entity id. Same-color calls are no-ops.
            PlayerGlowService glow = DMain.getInstance().getPlayerGlowService();
            if (glow != null) {
                NamedTextColor color = state.currentMode == 1 ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
                glow.setGlowing(target, player, color);
            }
        } else if (currentGlowTarget != null) {
            // No target anymore: clear glow
            state.clearGlowingTarget(player);
        }
    }

    // ---- Mode 3 pushback radius indicator -------------------------------------

    private void tickPushbackIndicator(Player player, PlayerState state) {
        // Clear any lingering glow from modes 1/2
        if (state.hasActiveGlow()) {
            state.clearGlowingTarget(player);
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

        void clearGlowingTarget(Player player) {
            if (lastGlowingTarget == null)
                return;
            try {
                if (player != null && player.isOnline()) {
                    PlayerGlowService glow = DMain.getInstance().getPlayerGlowService();
                    if (glow != null) {
                        glow.unsetGlowing(lastGlowingTarget, player);
                    }
                }
            } catch (Exception ignored) {
            }
            lastGlowingTarget = null;
        }
    }
}
