package dev.bukkit.game.boss.stalactite;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.bukkit.entity.boss.BukkitStalactiteEntity;

/**
 * Debug harness for the stalactite system.
 * <p>
 * Keeps a global registry of {@link StalactiteManager}s spawned via
 * <code>/d stalactite spawn</code>. Each manager is a "system" (one stalactite
 * above the player's head) that reuses the full growing/damageable/falling
 * logic and fires a debug impact message to its owner.
 * <p>
 * Bukkit-local, intentionally NOT tied to any BossStage/BossState.
 */
public final class DebugStalactiteService {

    private static DebugStalactiteService INSTANCE;

    public static DebugStalactiteService getInstance() {
        if (INSTANCE == null)
            INSTANCE = new DebugStalactiteService();
        return INSTANCE;
    }

    private static class DebugSystem {
        final UUID id;
        final UUID owner;
        final StalactiteManager manager;
        final Location origin; // player feet at spawn

        DebugSystem(UUID owner, Location origin, StalactiteManager manager) {
            this.id = UUID.randomUUID();
            this.owner = owner;
            this.origin = origin.clone();
            this.manager = manager;
        }
    }

    private final Map<UUID, DebugSystem> systems = new ConcurrentHashMap<>();

    private DebugStalactiteService() {
    }

    /**
     * Spawn a single-stalactite system 8 blocks above the player's current feet.
     * Ceiling = player feet + CEILING_OFFSET_Y; ground = ceiling - FALL_DISTANCE.
     * Uses the same visuals, health scaling and 20s regrow as the floor system.
     *
     * @return the spawned system id
     */
    public UUID spawnAt(Player player) {
        // Default: single stalactite that doesn't regrow, no debug hitbox (per spec: no
        // extra arg = single)
        return spawnAt(player, false, false);
    }

    /**
     * Spawn with explicit respawn/debug options.
     *
     * @param respawn if true stalactite instantly respawns as GROWING after impact
     *                (20s regrow); if false it's single-use
     * @param debug   if true hitbox highlights are shown
     */
    public UUID spawnAt(Player player, boolean respawn, boolean debug) {
        World world = player.getWorld();
        Location feet = player.getLocation().getBlock().getLocation().clone().add(0.5, 0, 0.5);
        // in case block location is air, use exact player loc for x/z and block y for
        // ground reference
        feet.setX(player.getLocation().getX());
        feet.setZ(player.getLocation().getZ());
        Location ceiling = feet.clone().add(0, StalactiteConfig.CEILING_OFFSET_Y, 0);

        // ensure ceiling chunk loaded and not inside block
        ceiling.setY(Math.min(world.getMaxHeight() - 2, ceiling.getY()));
        // nudge up if ceiling inside solid block
        int tries = 0;
        while (tries < 6 && !ceiling.getBlock().getType().isAir() && ceiling.getY() < world.getMaxHeight() - 1) {
            ceiling.add(0, 1, 0);
            tries++;
        }

        UUID owner = player.getUniqueId();
        String mode = (respawn ? "respawning" : "single") + (debug ? " debug" : "");
        // impact sends debug message to the owner (and any nearby player if owner
        // offline)
        StalactiteManager manager = new StalactiteManager(world, ceiling, (BukkitStalactiteEntity entity) -> {
            Player ownerPlayer = Bukkit.getPlayer(owner);
            Location ground = entity.getGround();
            // if owner offline, broadcast to world
            String msg = "§a[Stalactite Debug] §eImpact! §7Stalactite slammed into the ground — boss would be pushed §c-10% §7(height) §8(stacking allowed) §7@ "
                    + String.format("%.1f, %.1f, %.1f", ground.getX(), ground.getY(), ground.getZ());
            if (ownerPlayer != null && ownerPlayer.isOnline()) {
                ownerPlayer.sendMessage(msg);
                if (respawn) {
                    ownerPlayer.sendMessage("§7State: §eGROWING §7for 20s — not damageable until ripe. (" + mode + ")");
                } else {
                    ownerPlayer.sendMessage("§7State: §cSingle §7— despawned, no regrow. (" + mode + ")");
                }
            } else {
                world.getPlayers().forEach(p -> p.sendMessage(msg));
            }
        }, respawn, debug);

        manager.start();

        DebugSystem sys = new DebugSystem(owner, feet, manager);
        systems.put(sys.id, sys);
        String desc;
        if (respawn)
            desc = "§7(grows/falls/regrows 20s, flat -10% on impact)";
        else
            desc = "§7(single, grows/falls, no regrow, flat -10% on impact)";
        if (debug)
            desc += " §a[hitbox shown]";
        player.sendMessage("§a[Stalactite Debug] Spawned " + mode + " system §e" + shortId(sys.id) + " §aat "
                + blockStr(ceiling) + " " + desc + ". Use §e/d stalactite remove §7to clean up.");
        return sys.id;
    }

    /**
     * Remove the nearest debug system within 24 blocks of the player (owned or
     * not). If none nearby and the player owns systems, removes the owner's oldest.
     *
     * @return number removed (0 or 1)
     */
    public int removeAt(Player player) {
        if (systems.isEmpty())
            return 0;
        Location pLoc = player.getLocation();
        DebugSystem nearest = null;
        double best = Double.MAX_VALUE;
        for (DebugSystem sys : systems.values()) {
            // use ceiling location for distance
            Location ceil = sys.manager.getEntities().isEmpty() ? sys.origin
                    : sys.manager.getEntities().get(0).getCeiling();
            if (ceil == null || !ceil.getWorld().equals(pLoc.getWorld()))
                continue;
            double d = ceil.distanceSquared(pLoc);
            if (d < best && d < 24 * 24) {
                best = d;
                nearest = sys;
            }
        }
        if (nearest == null) {
            // fallback: any owned by this player
            for (DebugSystem sys : systems.values()) {
                if (sys.owner.equals(player.getUniqueId())) {
                    nearest = sys;
                    break;
                }
            }
        }
        if (nearest == null)
            return 0;
        nearest.manager.destroy();
        systems.remove(nearest.id);
        return 1;
    }

    /**
     * Remove one debug system by id.
     */
    public boolean removeById(UUID id) {
        DebugSystem sys = systems.remove(id);
        if (sys == null)
            return false;
        sys.manager.destroy();
        return true;
    }

    /**
     * Remove all debug systems.
     *
     * @return count removed
     */
    public int removeAll() {
        int n = systems.size();
        for (DebugSystem sys : new ArrayList<>(systems.values())) {
            try {
                sys.manager.destroy();
            } catch (Exception ignored) {
            }
        }
        systems.clear();
        return n;
    }

    public int count() {
        return systems.size();
    }

    public List<String> idShortList() {
        List<String> out = new ArrayList<>();
        for (UUID id : systems.keySet())
            out.add(shortId(id));
        return out;
    }

    public void shutdownAll() {
        removeAll();
    }

    // ---- helpers ----

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String blockStr(Location loc) {
        return String.format("%d, %d, %d in %s", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                loc.getWorld().getName());
    }

    // For testing / boss integration: expose system ids near location
    public List<UUID> nearbySystems(World world, Location loc, double radius) {
        List<UUID> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Map.Entry<UUID, DebugSystem> e : systems.entrySet()) {
            Location ceil = e.getValue().manager.getEntities().isEmpty() ? e.getValue().origin
                    : e.getValue().manager.getEntities().get(0).getCeiling();
            if (ceil != null && ceil.getWorld().equals(world) && ceil.distanceSquared(loc) <= r2)
                out.add(e.getKey());
        }
        return out;
    }

    // iterator for onDisable safety: also purge any stale managers whose world
    // unloaded
    public void purgeStale() {
        Iterator<Map.Entry<UUID, DebugSystem>> it = systems.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            boolean destroyed = e.getValue().manager.isDestroyed();
            if (destroyed)
                it.remove();
        }
    }
}
