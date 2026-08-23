package dev.bukkit.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.joml.AxisAngle4f;

/**
 * MVP HUD overlay using per-key stacked {@link TextDisplay}s that follow the
 * player's eye in front of the camera. Health/mana stays on the action bar
 * ({@code BukkitPlayerEntity}); this service is purely for extra ability info
 * like the hunter bow.
 *
 * <p>Each {@code key} gets its own display (stacked vertically). Persistent
 * fragments (duration 0) stay until explicit {@code hide} — used for
 * "while hunter bow held". Transient fragments expire automatically.
 * Privacy is MVP via {@code Player#hideEntity} for all non-owners.
 */
public final class HudOverlayService {

    private static HudOverlayService instance;

    /** Hard cap for the predictive velocity lead (blocks). */
    private static final double MAX_LEAD_OFFSET = 1.0;

    // ---- Config (loaded from hud.yml; defaults mirror former constants) ----
    private static volatile HudConfig config = HudConfig.defaults();

    private static Plugin plugin;
    private BukkitTask tickTask;

    private final Map<UUID, Map<String, OverlayFragment>> fragmentsByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, DisplayEntry>> displaysByPlayer = new HashMap<>();

    private HudOverlayService() {}

    public static synchronized HudOverlayService getInstance() {
        if (instance == null) {
            instance = new HudOverlayService();
        }
        return instance;
    }

    private static final class OverlayFragment {
        final String key;
        String text;
        long expiresAt; // Long.MAX_VALUE for persistent
        int priority;
        boolean persistent;
        long fadeOutMs; // trailing fade window; 0 = disappear instantly

        OverlayFragment(String key, String text, long expiresAt, int priority, boolean persistent) {
            this(key, text, expiresAt, priority, persistent, 0L);
        }

        OverlayFragment(String key, String text, long expiresAt, int priority, boolean persistent, long fadeOutMs) {
            this.key = key;
            this.text = text;
            this.expiresAt = expiresAt;
            this.priority = priority;
            this.persistent = persistent;
            this.fadeOutMs = fadeOutMs;
        }
    }

    /** A spawned TextDisplay plus the client-side state we last applied. */
    private static final class DisplayEntry {
        final TextDisplay entity;
        int appliedTeleportDuration = Integer.MIN_VALUE;
        int appliedOpacity = Integer.MIN_VALUE;

        DisplayEntry(TextDisplay entity) {
            this.entity = entity;
        }
    }

    public void init(Plugin owningPlugin) {
        init(owningPlugin, HudConfig.defaults());
    }

    public void init(Plugin owningPlugin, HudConfig cfg) {
        if (owningPlugin == null) return;
        plugin = owningPlugin;
        if (cfg != null) config = cfg;
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void reload(HudConfig cfg) {
        if (cfg != null) config = cfg;
        // reposition live displays with new geometry on next tick; force immediate for snappy reload
        for (UUID uuid : new HashSet<>(fragmentsByPlayer.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) reposition(p);
        }
    }

    public HudConfig getConfig() { return config; }

    public boolean isEnabled() { return config.enabled(); }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        // remove all displays
        for (Map<String, DisplayEntry> map : new ArrayList<>(displaysByPlayer.values())) {
            for (DisplayEntry entry : new ArrayList<>(map.values())) {
                if (entry.entity != null && entry.entity.isValid()) {
                    try { entry.entity.remove(); } catch (Exception ignored) {}
                }
            }
        }
        displaysByPlayer.clear();
        fragmentsByPlayer.clear();
        plugin = null;
    }

    /**
     * Show or update a HUD fragment. Duration 0 or negative = persistent until
     * explicit hide (used for "while bow held").
     */
    public void show(Player player, String key, String legacyText, long durationMs, int priority) {
        show(player, key, legacyText, durationMs, priority, 0L);
    }

    /**
     * Show or update a HUD fragment with an optional trailing fade-out. The
     * line stays fully opaque for {@code durationMs - fadeOutMs} and then
     * linearly fades to invisible across the final {@code fadeOutMs} window.
     */
    public void show(Player player, String key, String legacyText, long durationMs, int priority, long fadeOutMs) {
        if (player == null || key == null || legacyText == null) return;
        if (!player.isOnline()) return;
        // ensure on main thread
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> show(player, key, legacyText, durationMs, priority, fadeOutMs));
            return;
        }
        long now = System.currentTimeMillis();
        boolean persistent = durationMs <= 0;
        long expiresAt = persistent ? Long.MAX_VALUE : now + durationMs;
        long fade = persistent || fadeOutMs <= 0 ? 0L : Math.min(fadeOutMs, durationMs);
        Map<String, OverlayFragment> map = fragmentsByPlayer.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        OverlayFragment existing = map.get(key);
        if (existing != null) {
            existing.text = legacyText;
            existing.expiresAt = expiresAt;
            existing.priority = priority;
            existing.persistent = persistent;
            existing.fadeOutMs = fade;
        } else {
            map.put(key, new OverlayFragment(key, legacyText, expiresAt, priority, persistent, fade));
        }
        // ensure display exists/updated on next tick; do immediate reposition for snappy feedback
        reposition(player);
    }

    public void show(Player player, String key, String legacyText, long durationMs) {
        show(player, key, legacyText, durationMs, 0);
    }

    public void hide(Player player, String key) {
        if (player == null || key == null) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> hide(player, key));
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<String, OverlayFragment> fragMap = fragmentsByPlayer.get(uuid);
        if (fragMap != null) {
            fragMap.remove(key);
            if (fragMap.isEmpty()) {
                fragmentsByPlayer.remove(uuid);
            }
        }
        Map<String, DisplayEntry> dispMap = displaysByPlayer.get(uuid);
        if (dispMap != null) {
            DisplayEntry entry = dispMap.remove(key);
            removeSafely(entry);
            if (dispMap.isEmpty()) {
                displaysByPlayer.remove(uuid);
            } else {
                // re-stack remaining
                reposition(player);
            }
        }
    }

    public void clear(Player player) {
        if (player == null) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> clear(player));
            return;
        }
        UUID uuid = player.getUniqueId();
        fragmentsByPlayer.remove(uuid);
        Map<String, DisplayEntry> dispMap = displaysByPlayer.remove(uuid);
        if (dispMap != null) {
            for (DisplayEntry entry : dispMap.values()) {
                if (entry.entity != null && entry.entity.isValid()) entry.entity.remove();
            }
        }
    }

    /** Hide all existing displays from a newly joined player (MVP privacy). */
    public void hideAllFrom(Player viewer) {
        if (viewer == null || plugin == null) return;
        for (Map<String, DisplayEntry> map : displaysByPlayer.values()) {
            for (DisplayEntry entry : map.values()) {
                if (entry.entity != null && entry.entity.isValid()) {
                    try { viewer.hideEntity(plugin, entry.entity); } catch (Exception ignored) {}
                }
            }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        // copy keys to avoid CME
        Set<UUID> uuids = new HashSet<>(fragmentsByPlayer.keySet());
        // also need to tick displays that are orphaned (should be cleared)
        uuids.addAll(new HashSet<>(displaysByPlayer.keySet()));
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                // player offline: cleanup
                Map<String, DisplayEntry> dMap = displaysByPlayer.remove(uuid);
                if (dMap != null) {
                    for (DisplayEntry entry : dMap.values()) {
                        removeSafely(entry);
                    }
                }
                fragmentsByPlayer.remove(uuid);
                continue;
            }
            Map<String, OverlayFragment> fragMap = fragmentsByPlayer.get(uuid);
            Map<String, DisplayEntry> dispMap = displaysByPlayer.get(uuid);
            if (fragMap != null && !fragMap.isEmpty()) {
                // expire transients; drop their display entity in the same pass so
                // nothing can outlive its fragment (a fading line is removed exactly
                // at expiry)
                fragMap.entrySet().removeIf(e -> {
                    boolean expired = !e.getValue().persistent && now > e.getValue().expiresAt;
                    if (expired && dispMap != null) {
                        removeSafely(dispMap.remove(e.getKey()));
                    }
                    return expired;
                });
                if (fragMap.isEmpty()) {
                    fragmentsByPlayer.remove(uuid);
                }
            }
            // if no fragments left, ensure displays cleared
            Map<String, OverlayFragment> remaining = fragmentsByPlayer.get(uuid);
            if ((remaining == null || remaining.isEmpty()) && dispMap != null) {
                for (DisplayEntry entry : dispMap.values()) {
                    removeSafely(entry);
                }
                displaysByPlayer.remove(uuid);
                continue;
            }
            if (remaining != null && !remaining.isEmpty()) {
                // cull to maxKeys by priority
                int maxKeys = config.maxKeys();
                if (remaining.size() > maxKeys) {
                    List<OverlayFragment> sorted = new ArrayList<>(remaining.values());
                    sorted.sort(Comparator.comparingInt((OverlayFragment f) -> f.priority).reversed());
                    Set<String> keep = new HashSet<>();
                    for (int i = 0; i < maxKeys; i++) keep.add(sorted.get(i).key);
                    // remove low-priority beyond max along with their displays
                    remaining.keySet().retainAll(keep);
                    if (dispMap != null) {
                        dispMap.entrySet().removeIf(e -> {
                            if (!remaining.containsKey(e.getKey())) {
                                removeSafely(e.getValue());
                                return true;
                            }
                            return false;
                        });
                    }
                }
                reposition(player);
            } else if (dispMap != null) {
                // no frag but display exists (edge) -> reposition still to move, but will be cleared next loop
                reposition(player);
            }
        }
    }

    /** Removes a display entity server-side; never leaves ghosts behind. */
    private static void removeSafely(DisplayEntry entry) {
        if (entry != null && entry.entity != null && entry.entity.isValid()) {
            try { entry.entity.remove(); } catch (Exception ignored) {}
        }
    }

    private void reposition(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, OverlayFragment> fragMap = fragmentsByPlayer.get(uuid);
        if (fragMap == null || fragMap.isEmpty()) {
            // no frags -> ensure displays cleared (handled by caller)
            return;
        }
        if (!config.enabled()) {
            // enabled false: hide any displays but keep fragments for re-enable
            Map<String, DisplayEntry> existing = displaysByPlayer.remove(uuid);
            if (existing != null) {
                for (DisplayEntry entry : existing.values()) {
                    removeSafely(entry);
                }
            }
            return;
        }
        // sort by priority desc, then key for stability
        List<OverlayFragment> sorted = new ArrayList<>(fragMap.values());
        sorted.sort(Comparator.comparingInt((OverlayFragment f) -> f.priority).reversed().thenComparing(f -> f.key));

        Map<String, DisplayEntry> dispMap = displaysByPlayer.computeIfAbsent(uuid, k -> new HashMap<>());

        // ensure world valid
        World world = player.getWorld();
        Location base = calcHudTarget(player);
        long now = System.currentTimeMillis();
        HudConfig.Tracking tracking = config.tracking();

        // remove any displays whose fragment is gone (expired/culled/hid) —
        // a plain keySet().retainAll here would orphan the entity in-world
        dispMap.entrySet().removeIf(e -> {
            if (!fragMap.containsKey(e.getKey())) {
                removeSafely(e.getValue());
                return true;
            }
            return false;
        });

        for (int i = 0; i < sorted.size(); i++) {
            OverlayFragment frag = sorted.get(i);
            DisplayEntry entry = dispMap.get(frag.key);
            Location loc = base.clone().add(0, i * config.spacing(), 0);
            if (entry == null || !entry.entity.isValid() || entry.entity.getWorld() != world) {
                // remove invalid if present
                if (entry != null && entry.entity.isValid() && entry.entity.getWorld() != world) {
                    entry.entity.remove();
                }
                TextDisplay display = spawnDisplay(player, frag.text, loc);
                entry = new DisplayEntry(display);
                entry.appliedTeleportDuration = config.teleportDuration();
                entry.appliedOpacity = HudTracking.OPAQUE;
                dispMap.put(frag.key, entry);
                // MVP privacy: hide from all others
                hideFromOthers(player, display);
                continue;
            }
            // update text if changed
            if (!frag.text.equals(entry.entity.getText())) {
                entry.entity.setText(frag.text);
            }
            applyFade(entry, frag, now);
            moveDisplay(entry, loc, config.teleportDuration(), tracking.minStep(), tracking.snapDistance());
        }
        // if more displays than fragments (should not happen due to the sweep above), remove extras
        if (dispMap.size() > sorted.size()) {
            Set<String> keepKeys = new HashSet<>();
            for (OverlayFragment f : sorted) keepKeys.add(f.key);
            dispMap.entrySet().removeIf(e -> {
                if (!keepKeys.contains(e.getKey())) {
                    removeSafely(e.getValue());
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Real-time follow update for one display. Three regimes:
     * <ol>
     * <li><b>SNAP</b> — delta beyond snap-distance (player teleport, world
     * change, raytrace jump): hard-place with teleport-duration 0 so the line
     * never glides across the world.</li>
     * <li><b>SKIP</b> — delta below min-step: send nothing. Re-teleporting on
     * sub-perceptual deltas restarts the client interpolation every tick and
     * is what makes an un-interpolated HUD look like it is stuttering/lagging
     * behind player movement.</li>
     * <li><b>SMOOTH</b> — normal per-tick follow; combined with a non-zero
     * teleport-duration and the velocity lead in {@link #calcHudTarget} this
     * reads as glued to the camera without visible stepping.</li>
     * </ol>
     */
    private void moveDisplay(DisplayEntry entry, Location target, int smoothDuration,
                             double minStep, double snapDistance) {
        Location current = entry.entity.getLocation();
        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        HudTracking.Move move = HudTracking.decideMove(distSq, minStep, snapDistance);
        if (move == HudTracking.Move.SKIP) {
            return;
        }
        int wantedDuration = move == HudTracking.Move.SNAP ? 0 : smoothDuration;
        if (entry.appliedTeleportDuration != wantedDuration) {
            try { entry.entity.setTeleportDuration(wantedDuration); } catch (Exception ignored) {}
            entry.appliedTeleportDuration = wantedDuration;
        }
        entry.entity.teleport(target);
    }

    /**
     * Drives the trailing fade-out of transient fragments via text opacity.
     * Persistent fragments and fragments outside their fade window are forced
     * back to fully opaque (covers show()-refreshes of a fading line).
     */
    private void applyFade(DisplayEntry entry, OverlayFragment frag, long now) {
        long remaining = frag.persistent ? Long.MAX_VALUE : frag.expiresAt - now;
        int opacity = HudTracking.fadeOpacityByte(remaining, frag.fadeOutMs);
        if (entry.appliedOpacity != opacity) {
            try { entry.entity.setTextOpacity((byte) opacity); } catch (Exception ignored) {}
            entry.appliedOpacity = opacity;
        }
    }

    /**
     * Eye-front anchor point with wall raytrace plus predictive velocity lead.
     */
    private Location calcHudTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double dist = config.distance();
        World world = player.getWorld();
        // raytrace to avoid clipping through walls
        try {
            RayTraceResult result = world.rayTraceBlocks(eye, dir, config.distance());
            if (result != null && result.getHitPosition() != null) {
                Location hitLoc = result.getHitPosition().toLocation(world);
                double hitDist = eye.distance(hitLoc);
                dist = Math.max(0.4, hitDist - 0.22);
            }
        } catch (Exception ignored) {}
        Location loc = eye.clone().add(dir.multiply(dist));
        loc.add(0, config.verticalOffset(), 0);
        // Predictive lead: the server observes a moving player ~1-2 ticks late
        // (client prediction + network). Anchoring exactly at the observed eye
        // position makes even perfectly interpolated displays trail behind the
        // camera while running/sprinting. Leading along the velocity cancels
        // that lag during straight-line motion; clamped so knockback/falls or
        // high-latency spikes cannot fling the HUD away.
        double leadTicks = config.tracking().velocityLeadTicks();
        if (leadTicks > 0) {
            Vector v = player.getVelocity();
            if (!v.isZero()) {
                Vector lead = HudTracking.lead(v, leadTicks, MAX_LEAD_OFFSET);
                loc.add(lead);
            }
        }
        return loc;
    }

    private TextDisplay spawnDisplay(Player owner, String text, Location loc) {
        World world = loc.getWorld();
        if (world == null) world = owner.getWorld();
        return world.spawn(loc, TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(config.seeThrough());
            d.setShadowed(config.shadowed());
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setLineWidth(config.lineWidth());
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            try { d.setTextOpacity((byte) HudTracking.OPAQUE); } catch (Exception ignored) {}
            // background with alpha
            try {
                d.setBackgroundColor(config.bgColor());
            } catch (Exception ignored) {
                try { d.setBackgroundColor(Color.fromARGB(96, 0, 0, 0)); } catch (Exception e2) {}
            }
            try { d.setBrightness(new Display.Brightness(config.brightness(), config.brightness())); } catch (Exception ignored) {}
            try { d.setViewRange(config.viewRange()); } catch (Exception ignored) {}
            try { d.setInterpolationDuration(config.interpolationDuration()); } catch (Exception ignored) {}
            try { d.setTeleportDuration(config.teleportDuration()); } catch (Exception ignored) {}
            Transformation tf = d.getTransformation();
            tf.getScale().set(config.scale(), config.scale(), config.scale());
            // ensure translation zero
            tf.getTranslation().set(0, 0, 0);
            tf.getLeftRotation().set(new AxisAngle4f(0, 0, 0, 1));
            tf.getRightRotation().set(new AxisAngle4f(0, 0, 0, 1));
            d.setTransformation(tf);
        });
    }

    private void hideFromOthers(Player owner, TextDisplay display) {
        if (plugin == null || display == null || !display.isValid()) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(owner)) {
                // ensure owner can see
                try { other.showEntity(plugin, display); } catch (Exception ignored) {}
                continue;
            }
            try { other.hideEntity(plugin, display); } catch (Exception ignored) {}
        }
    }

    /** For tests / debugging */
    public int getActiveCount(Player player) {
        Map<String, OverlayFragment> m = fragmentsByPlayer.get(player.getUniqueId());
        return m == null ? 0 : m.size();
    }
}
