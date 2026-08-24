package dev.bukkit.utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.bukkit.item.BukkitItemStackAdapter;

/**
 * Central stealth / shroud registry for Orb of Stealth.
 * Tracks passive holders (20% dodge) and active smoke shrouds (r=5, 6s).
 * Checked by targeting code and the smoke effect.
 */
public final class StealthRegistry {

    private StealthRegistry() {}

    // passive: set of holders that currently have ORB_STEALTH_PASSIVE equipped
    private static final Set<UUID> PASSIVE_HOLDERS = ConcurrentHashMap.newKeySet();

    // shroud per player
    private static final Map<UUID, Shroud> SHROUDS = new ConcurrentHashMap<>();

    private static final class Shroud {
        Location center;
        double radiusSq; // 25 for r=5
        long expiryMs;
        long revealUntilMs; // briefly visible after attack/outside
        // transient outside flag for re-enter detection handled by effect
    }

    // ---- passive ----

    public static void setPassiveEquipped(UUID uuid, boolean equipped) {
        if (equipped) PASSIVE_HOLDERS.add(uuid);
        else PASSIVE_HOLDERS.remove(uuid);
    }

    public static boolean isPassiveEquipped(UUID uuid) {
        return PASSIVE_HOLDERS.contains(uuid);
    }

    /** 20% roll — called on each targeting attempt for passive holders. */
    public static boolean rollPassiveDodge(UUID uuid) {
        if (!isPassiveEquipped(uuid)) return false;
        return Math.random() < 0.20;
    }

    // ---- shroud ----

    public static void placeShroud(UUID uuid, Location center, double radius, long durationMs) {
        Shroud s = new Shroud();
        s.center = center.clone();
        s.radiusSq = radius * radius;
        s.expiryMs = System.currentTimeMillis() + durationMs;
        s.revealUntilMs = 0;
        SHROUDS.put(uuid, s);
    }

    public static void removeShroud(UUID uuid) {
        SHROUDS.remove(uuid);
    }

    /** Briefly reveal (e.g. on attack or leaving shroud). */
    public static void reveal(UUID uuid, long durationMs) {
        Shroud s = SHROUDS.get(uuid);
        if (s != null) {
            s.revealUntilMs = System.currentTimeMillis() + durationMs;
        }
    }

    public static boolean hasShroud(UUID uuid) {
        Shroud s = SHROUDS.get(uuid);
        return s != null && System.currentTimeMillis() < s.expiryMs;
    }

    /** Whether player is currently considered hidden inside shroud (invisible to mobs). */
    public static boolean isShrouded(Player player) {
        if (player == null) return false;
        return isShrouded(player.getUniqueId(), player.getLocation());
    }

    public static boolean isShrouded(UUID uuid, Location loc) {
        Shroud s = SHROUDS.get(uuid);
        if (s == null) return false;
        long now = System.currentTimeMillis();
        if (now >= s.expiryMs) {
            SHROUDS.remove(uuid);
            return false;
        }
        if (now < s.revealUntilMs) return false; // briefly visible
        if (s.center == null || s.center.getWorld() == null || loc.getWorld()==null) return false;
        if (!s.center.getWorld().equals(loc.getWorld())) return false;
        return loc.distanceSquared(s.center) <= s.radiusSq;
    }

    /** Whether target player is effectively stealthed (passive dodge won OR shrouded). For targeting checks. */
    public static boolean isStealthedForTargeting(Player target) {
        if (target == null) return false;
        UUID uuid = target.getUniqueId();
        // shroud takes precedence (if inside and not revealed, definitely hidden)
        if (isShrouded(target)) return true;
        // passive 20% — roll per check. Caller should treat as cancel chance.
        // We provide method that does roll.
        return false; // passive handled via rollPassiveDodge separate
    }

    /** Inventory check for orb passive — true if ORB_OF_STEALTH anywhere in inventory. */
    public static boolean hasOrbInInventory(Player player) {
        if (player == null) return false;
        try {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && "ORB_OF_STEALTH".equals(BukkitItemStackAdapter.getRpgItemId(stack))) return true;
            }
            for (ItemStack stack : player.getInventory().getArmorContents()) {
                if (stack != null && "ORB_OF_STEALTH".equals(BukkitItemStackAdapter.getRpgItemId(stack))) return true;
            }
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && "ORB_OF_STEALTH".equals(BukkitItemStackAdapter.getRpgItemId(off))) return true;
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main != null && "ORB_OF_STEALTH".equals(BukkitItemStackAdapter.getRpgItemId(main))) return true;
        } catch (Exception ignored) {}
        return false;
    }

    /** Combined check used by targeting patches: true if target should NOT be selected. */
    public static boolean shouldHideFromMob(Player target) {
        if (target == null) return false;
        if (isShrouded(target)) return true;
        // passive 20% roll if orb is held (equipped) OR anywhere in inventory
        boolean hasOrb = isPassiveEquipped(target.getUniqueId()) || hasOrbInInventory(target);
        if (hasOrb && Math.random() < 0.20) return true;
        return false;
    }

    /** Whether target is shrouded or would dodge passive — for damage cancellation. */
    public static boolean shouldBlockDamage(Player target) {
        if (target == null) return false;
        if (isShrouded(target)) return true;
        // passive does NOT block damage directly, only targeting chance — keep as targeting only
        return false;
    }

    /** Clear any mob targeting this player (called on re-enter). */
    public static void clearAggro(Player player) {
        if (player == null) return;
        // Bukkit mobs
        for (World w : Bukkit.getWorlds()) {
            for (Mob mob : w.getEntitiesByClass(Mob.class)) {
                try {
                    LivingEntity t = mob.getTarget();
                    if (t != null && t.getUniqueId().equals(player.getUniqueId())) {
                        mob.setTarget(null);
                    }
                } catch (Exception ignored) {}
            }
        }
        // Also clear via BukkitPlayerEntity.clearMobTargetsOf if desired, but above covers.
    }

    public static Location getShroudCenter(UUID uuid) {
        Shroud s = SHROUDS.get(uuid);
        return s == null ? null : s.center;
    }

    public static double getShroudRadius(UUID uuid) {
        Shroud s = SHROUDS.get(uuid);
        return s == null ? 0 : Math.sqrt(s.radiusSq);
    }

    public static long getRemainingMs(UUID uuid) {
        Shroud s = SHROUDS.get(uuid);
        if (s == null) return 0;
        return Math.max(0, s.expiryMs - System.currentTimeMillis());
    }

    /** For testing / quit cleanup */
    public static void clearAll(UUID uuid) {
        PASSIVE_HOLDERS.remove(uuid);
        SHROUDS.remove(uuid);
    }
}
