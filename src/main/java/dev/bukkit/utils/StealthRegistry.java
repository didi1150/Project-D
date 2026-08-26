package dev.bukkit.utils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.bukkit.item.BukkitItemStackAdapter;

/**
 * Central stealth / shroud registry for Orb of Stealth.
 * Tracks passive holders (20% dodge) and active smoke shrouds (r=7, 6s).
 * Checked by targeting code and the smoke effect.
 */
public final class StealthRegistry {

    private StealthRegistry() {}

    /**
     * Injectable clock so tests can time-travel expiry/reveal windows. Public
     * purely as a test seam; production always leaves it at the wall clock.
     */
    public static LongSupplier CLOCK = System::currentTimeMillis;

    private static final double PASSIVE_DODGE_CHANCE = 0.20;

    /**
     * How long one passive-dodge verdict stays stable. Callers poll these checks
     * several times per targeting attempt (target event handler, ability aiming,
     * boss strategies); without caching each poll rolled its own 20% dice, which
     * compounded into a far higher effective dodge. One verdict per window keeps
     * every consumer of an acquisition consistent.
     */
    static final long ROLL_TTL_MS = 400L;

    // passive: set of holders that currently have ORB_STEALTH_PASSIVE equipped
    private static final Set<UUID> PASSIVE_HOLDERS = ConcurrentHashMap.newKeySet();

    // passive dodge verdict cache: uuid -> [verdict(1=true/0=false), validUntilMs]
    private static final Map<UUID, long[]> PASSIVE_ROLLS = new ConcurrentHashMap<>();

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
        if (equipped) {
            PASSIVE_HOLDERS.add(uuid);
        } else {
            PASSIVE_HOLDERS.remove(uuid);
            PASSIVE_ROLLS.remove(uuid);
        }
    }

    public static boolean isPassiveEquipped(UUID uuid) {
        return PASSIVE_HOLDERS.contains(uuid);
    }

    /**
     * 20% dodge verdict for passive holders — stable within the TTL window so
     * repeated polls during one targeting attempt get the same answer.
     */
    public static boolean rollPassiveDodge(UUID uuid) {
        if (uuid == null || !isPassiveEquipped(uuid)) return false;
        return cachedResult(uuid);
    }

    private static boolean cachedResult(UUID uuid) {
        long now = CLOCK.getAsLong();
        long[] cached = PASSIVE_ROLLS.get(uuid);
        if (cached != null && now < cached[1]) {
            return cached[0] == 1;
        }
        boolean verdict = Math.random() < PASSIVE_DODGE_CHANCE;
        PASSIVE_ROLLS.put(uuid, new long[]{verdict ? 1 : 0, now + ROLL_TTL_MS});
        return verdict;
    }

    // ---- shroud ----

    public static void placeShroud(UUID uuid, Location center, double radius, long durationMs) {
        Shroud s = new Shroud();
        s.center = center.clone();
        s.radiusSq = radius * radius;
        s.expiryMs = CLOCK.getAsLong() + durationMs;
        s.revealUntilMs = 0;
        SHROUDS.put(uuid, s);
    }

    public static void removeShroud(UUID uuid) {
        SHROUDS.remove(uuid);
        PASSIVE_ROLLS.remove(uuid);
    }

    /** Briefly reveal (e.g. on attack or leaving shroud). */
    public static void reveal(UUID uuid, long durationMs) {
        Shroud s = SHROUDS.get(uuid);
        if (s != null) {
            s.revealUntilMs = CLOCK.getAsLong() + durationMs;
        }
    }

    public static boolean hasShroud(UUID uuid) {
        Shroud s = SHROUDS.get(uuid);
        return s != null && CLOCK.getAsLong() < s.expiryMs;
    }

    /** Whether player is currently considered hidden inside shroud (invisible to mobs). */
    public static boolean isShrouded(Player player) {
        if (player == null) return false;
        return isShrouded(player.getUniqueId(), player.getLocation());
    }

    public static boolean isShrouded(UUID uuid, Location loc) {
        Shroud s = SHROUDS.get(uuid);
        if (s == null) return false;
        long now = CLOCK.getAsLong();
        if (now >= s.expiryMs) {
            SHROUDS.remove(uuid);
            return false;
        }
        if (now < s.revealUntilMs) return false; // briefly visible
        if (s.center == null || s.center.getWorld() == null || loc.getWorld()==null) return false;
        if (!s.center.getWorld().equals(loc.getWorld())) return false;
        return loc.distanceSquared(s.center) <= s.radiusSq;
    }

    /**
     * Deterministic stealth answer for callers that must never roll dice
     * (threat rerolls, tank selection, boss targeting). True only while the
     * player stands inside a live, unrevealed shroud.
     */
    public static boolean isShroudedDeterministic(Player player) {
        return isShrouded(player);
    }

    /** Whether the player's shroud reveal window is currently open (attack/exit penalty). */
    public static boolean isRevealed(UUID uuid) {
        Shroud s = SHROUDS.get(uuid);
        if (s == null) return false;
        long now = CLOCK.getAsLong();
        return now < s.revealUntilMs && now < s.expiryMs;
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
        // passive dodge verdict if orb is held (equipped) OR anywhere in inventory —
        // cached so every consumer of one targeting attempt sees the same answer
        UUID uuid = target.getUniqueId();
        if (!isPassiveEquipped(uuid) && !hasOrbInInventory(target)) {
            PASSIVE_ROLLS.remove(uuid);
            return false;
        }
        return cachedResult(uuid);
    }

    /** Clear any mob targeting this player (called on cast/re-enter and by the shroud safety sweep). */
    public static void clearAggro(Player player) {
        if (player == null) return;
        // Bukkit mobs
        for (World w : Bukkit.getWorlds()) {
            for (Mob mob : w.getEntitiesByClass(Mob.class)) {
                try {
                    detachMob(mob, player.getUniqueId());
                } catch (Exception ignored) {}
            }
        }
        // Also clear via BukkitPlayerEntity.clearMobTargetsOf if desired, but above covers.
    }

    /**
     * Drop the given player as this mob's target. Zombified piglins additionally
     * get their anger defused: they are brain-based mobs that re-acquire targets
     * straight out of the Universal Anger memory even while the target is null,
     * so {@code setTarget(null)} alone is a treadmill — the anger-driven
     * acquisition path can bypass {@code EntityTargetLivingEntityEvent}, leaving
     * both the target-event gate and this sweep unable to hold them off a
     * shrouded player.
     */
    public static void detachMob(Mob mob, UUID playerUniqueId) {
        if (mob == null || playerUniqueId == null) return;
        LivingEntity target = mob.getTarget();
        if (target == null || !playerUniqueId.equals(target.getUniqueId())) return;
        mob.setTarget(null);
        if (mob instanceof PigZombie pigman) {
            pigman.setAngry(false);
        }
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
        return Math.max(0, s.expiryMs - CLOCK.getAsLong());
    }

    /** For testing / quit cleanup */
    public static void clearAll(UUID uuid) {
        PASSIVE_HOLDERS.remove(uuid);
        PASSIVE_ROLLS.remove(uuid);
        SHROUDS.remove(uuid);
    }
}
