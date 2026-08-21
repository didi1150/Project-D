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

    // ---- Config (loaded from hud.yml; defaults mirror former constants) ----
    private static volatile HudConfig config = HudConfig.defaults();

    private static Plugin plugin;
    private BukkitTask tickTask;

    private final Map<UUID, Map<String, OverlayFragment>> fragmentsByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, TextDisplay>> displaysByPlayer = new HashMap<>();

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

        OverlayFragment(String key, String text, long expiresAt, int priority, boolean persistent) {
            this.key = key;
            this.text = text;
            this.expiresAt = expiresAt;
            this.priority = priority;
            this.persistent = persistent;
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
        for (Map<String, TextDisplay> map : new ArrayList<>(displaysByPlayer.values())) {
            for (TextDisplay d : new ArrayList<>(map.values())) {
                if (d != null && d.isValid()) {
                    try { d.remove(); } catch (Exception ignored) {}
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
        if (player == null || key == null || legacyText == null) return;
        if (!player.isOnline()) return;
        // ensure on main thread
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> show(player, key, legacyText, durationMs, priority));
            return;
        }
        long now = System.currentTimeMillis();
        boolean persistent = durationMs <= 0;
        long expiresAt = persistent ? Long.MAX_VALUE : now + durationMs;
        Map<String, OverlayFragment> map = fragmentsByPlayer.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        OverlayFragment existing = map.get(key);
        if (existing != null) {
            existing.text = legacyText;
            existing.expiresAt = expiresAt;
            existing.priority = priority;
            existing.persistent = persistent;
        } else {
            map.put(key, new OverlayFragment(key, legacyText, expiresAt, priority, persistent));
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
        Map<String, TextDisplay> dispMap = displaysByPlayer.get(uuid);
        if (dispMap != null) {
            TextDisplay d = dispMap.remove(key);
            if (d != null && d.isValid()) {
                d.remove();
            }
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
        Map<String, TextDisplay> dispMap = displaysByPlayer.remove(uuid);
        if (dispMap != null) {
            for (TextDisplay d : dispMap.values()) {
                if (d != null && d.isValid()) d.remove();
            }
        }
    }

    /** Hide all existing displays from a newly joined player (MVP privacy). */
    public void hideAllFrom(Player viewer) {
        if (viewer == null || plugin == null) return;
        for (Map<String, TextDisplay> map : displaysByPlayer.values()) {
            for (TextDisplay d : map.values()) {
                if (d != null && d.isValid()) {
                    try { viewer.hideEntity(plugin, d); } catch (Exception ignored) {}
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
                Map<String, TextDisplay> dMap = displaysByPlayer.remove(uuid);
                if (dMap != null) for (TextDisplay d : dMap.values()) if (d != null && d.isValid()) d.remove();
                fragmentsByPlayer.remove(uuid);
                continue;
            }
            Map<String, OverlayFragment> fragMap = fragmentsByPlayer.get(uuid);
            if (fragMap != null && !fragMap.isEmpty()) {
                // expire transients
                boolean removed = fragMap.entrySet().removeIf(e -> !e.getValue().persistent && now > e.getValue().expiresAt);
                if (fragMap.isEmpty()) {
                    fragmentsByPlayer.remove(uuid);
                }
                // if all expired, displays will be cleaned below via reposition/clear logic
            }
            // if no fragments left, ensure displays cleared
            Map<String, OverlayFragment> remaining = fragmentsByPlayer.get(uuid);
            Map<String, TextDisplay> dispMap = displaysByPlayer.get(uuid);
            if ((remaining == null || remaining.isEmpty()) && dispMap != null) {
                for (TextDisplay d : dispMap.values()) if (d != null && d.isValid()) d.remove();
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
                    // remove low-priority beyond max
                    remaining.keySet().retainAll(keep);
                    if (dispMap != null) {
                        // remove displays for culled keys
                        dispMap.keySet().retainAll(keep);
                        // actually remove entities for culled
                        // we retained, so need to remove those not kept - already handled via retain on remaining, but dispMap still has extra; iterate
                    }
                    // clean displays for removed keys
                    if (dispMap != null) {
                        dispMap.entrySet().removeIf(e -> {
                            if (!remaining.containsKey(e.getKey())) {
                                if (e.getValue() != null && e.getValue().isValid()) e.getValue().remove();
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

    private void reposition(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, OverlayFragment> fragMap = fragmentsByPlayer.get(uuid);
        if (fragMap == null || fragMap.isEmpty()) {
            // no frags -> ensure displays cleared (handled by caller)
            return;
        }
        if (!config.enabled()) {
            // enabled false: hide any displays but keep fragments for re-enable
            Map<String, TextDisplay> existing = displaysByPlayer.remove(uuid);
            if (existing != null) for (TextDisplay d : existing.values()) if (d != null && d.isValid()) d.remove();
            return;
        }
        // sort by priority desc, then key for stability
        List<OverlayFragment> sorted = new ArrayList<>(fragMap.values());
        sorted.sort(Comparator.comparingInt((OverlayFragment f) -> f.priority).reversed().thenComparing(f -> f.key));

        Map<String, TextDisplay> dispMap = displaysByPlayer.computeIfAbsent(uuid, k -> new HashMap<>());

        // ensure world valid
        World world = player.getWorld();
        Location base = calcHudLoc(player);

        // hide any displays for keys not in sorted (should be cleaned but safe)
        dispMap.keySet().retainAll(fragMap.keySet());

        for (int i = 0; i < sorted.size(); i++) {
            OverlayFragment frag = sorted.get(i);
            TextDisplay display = dispMap.get(frag.key);
            Location loc = base.clone().add(0, i * config.spacing(), 0);
            if (display == null || !display.isValid() || display.getWorld() != world) {
                // remove invalid if present
                if (display != null && display.isValid() && display.getWorld() != world) {
                    display.remove();
                }
                display = spawnDisplay(player, frag.text, loc);
                dispMap.put(frag.key, display);
                // MVP privacy: hide from all others
                hideFromOthers(player, display);
            } else {
                // update text if changed
                if (!frag.text.equals(display.getText())) {
                    display.setText(frag.text);
                }
                // teleport - only if changed significantly to reduce packets? always teleport for HUD follow
                display.teleport(loc);
            }
        }
        // if more displays than fragments (should not happen due to retain), remove extras
        if (dispMap.size() > sorted.size()) {
            Set<String> keepKeys = new HashSet<>();
            for (OverlayFragment f : sorted) keepKeys.add(f.key);
            dispMap.entrySet().removeIf(e -> {
                if (!keepKeys.contains(e.getKey())) {
                    if (e.getValue() != null && e.getValue().isValid()) e.getValue().remove();
                    return true;
                }
                return false;
            });
        }
    }

    private Location calcHudLoc(Player player) {
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
