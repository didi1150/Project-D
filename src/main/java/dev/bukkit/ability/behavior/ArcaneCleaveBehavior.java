package dev.bukkit.ability.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Per-holder behavior for the {@code ARCANE_CLEAVE} passive: two arcane-blade
 * hits charge the rune and morph the blade to its diamond form; a hit landed
 * while charged (diamond material) releases a forward half-circle magic cleave
 * and consumes the charge, reverting the blade to iron. The counter is tracked
 * per holder.
 *
 * <p>The charged morph is a toggled state with its own duration
 * ({@link #DIAMOND_DURATION_TICKS}): switching the blade away mid-charge keeps
 * it diamond, and the scheduled revert fades it back to iron on schedule even
 * while unequipped. Quitting reverts immediately (timers do not survive
 * relogin).</p>
 */
public class ArcaneCleaveBehavior implements AbilityBehavior {

    public static final String ITEM_ID = "ARCANE_BLADE"; // for morph checks

    private static final int CLEAVE_INTERVAL = 2;
    private static final double CLEAVE_RADIUS = 2.5;
    private static final double CLEAVE_FORWARD_STEP = 0.65;
    private static final int CLEAVE_TICKS = 12;
    private static final double CLEAVE_DAMAGE_MULTIPLIER = 1.0;
    private static final double CLEAVE_Y_TOLERANCE = 2.2;
    private static final long DIAMOND_DURATION_TICKS = 20L * 5;
    /** Cosmetic delay between releasing the cleave and reverting to iron. */
    private static final long RELEASE_REVERT_DELAY_TICKS = 6L;
    /**
     * Vanilla invulnerability makes two direct melee hits on the SAME victim
     * within this window impossible; a repeat is a duplicate dispatch of one
     * physical swing and must not count twice.
     */
    private static final long DUPLICATE_HIT_WINDOW_MS = 100L;

    private ActiveAbility ctx;
    private int hitCount = 0;
    private BukkitTask pendingRevert;
    private BukkitTask pendingConsume;
    private int morphedSlot = Integer.MIN_VALUE; // -1 = off hand
    /**
     * True while a released cleave waits for its delayed cosmetic revert. The
     * material is still DIAMOND_SWORD inside that window, so the charged-hit
     * branch must be blocked to prevent a second wave from the same charge.
     */
    private boolean releasing = false;
    /** Victim uuid -> last counted hit timestamp (duplicate dispatch guard). */
    private final Map<UUID, Long> recentVictimHits = new HashMap<>();

    public ArcaneCleaveBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(
                new EventAction<>(this::onDamage, EntityDamageByEntityEvent.class, EventAction.HIGHEST_PRIORITY));
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onQuit, PlayerQuitEvent.class));
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        hitCount = 0;
        recentVictimHits.clear();
        // The charged (diamond) morph is a toggled state and deliberately
        // survives unequip: pendingRevert/pendingConsume are plain scheduler
        // tasks keyed by player uuid + slot, so the blade still fades back to
        // iron after DIAMOND_DURATION_TICKS even while stowed, and re-equipping
        // within that window resumes the charged state. Only quitting takes the
        // explicit revert path (see onQuit), because scheduled tasks die with
        // the server session and would leave a permanently diamond blade.
    }

    private void onQuit(PlayerQuitEvent e) {
        if (!e.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        // session end: the morph's fade timer does not survive relogin
        revertStuckMorph();
        cancelPendingRevert();
        cancelPendingConsume();
        releasing = false;
    }

    private void cancelPendingRevert() {
        if (pendingRevert != null) {
            try {
                pendingRevert.cancel();
            } catch (Exception ignored) {
            }
            pendingRevert = null;
        }
    }

    private void cancelPendingConsume() {
        if (pendingConsume != null) {
            try {
                pendingConsume.cancel();
            } catch (Exception ignored) {
            }
            pendingConsume = null;
        }
    }

    /**
     * Best-effort restore of a still-charged blade to its iron form when the
     * behavior deactivates (unequip, hotbar switch away, quit).
     */
    private void revertStuckMorph() {
        boolean charged = morphedSlot != Integer.MIN_VALUE || releasing;
        morphedSlot = Integer.MIN_VALUE;
        releasing = false;
        if (!charged)
            return;
        try {
            Player pl = Bukkit.getPlayer(ctx.getHolder().getUuid());
            if (pl == null || !pl.isOnline())
                return;
            ItemStack main = pl.getInventory().getItemInMainHand();
            if (isArcaneBlade(main) && main.getType() == Material.DIAMOND_SWORD) {
                pl.getInventory().setItemInMainHand(copyWithMaterial(main, Material.IRON_SWORD));
            } else {
                ItemStack off = pl.getInventory().getItemInOffHand();
                if (isArcaneBlade(off) && off.getType() == Material.DIAMOND_SWORD)
                    pl.getInventory().setItemInOffHand(copyWithMaterial(off, Material.IRON_SWORD));
            }
            pl.updateInventory();
        } catch (Exception ignored) {
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled())
            return;
        // Hits on RPG-managed victims (dungeon mobs, boss) arrive rewritten to
        // DamageUtils.RPG_HANDLED_ENTITY after CombatListener applied the real
        // damage through the RPG pipeline — those are genuine swings and must
        // charge/release the blade, not be dropped as negligible.
        if (!DamageUtils.isChargeableHit(event))
            return;
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK)
            return; // only direct hits for cleave

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (!(damager instanceof Player player) || !(victim instanceof LivingEntity livingVictim))
            return;
        if (player.isDead() || !player.isOnline())
            return;
        if (victim instanceof Player && CombatRelation.isPlayerTeam(victim))
            return;

        RPGEntity holder = ctx.getHolder();
        if (!holder.getUuid().equals(player.getUniqueId()))
            return;
        if (!holder.isAlive() || EntityManager.getInstance().isGhost(player.getUniqueId()))
            return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isArcaneBlade(held))
            return;

        // One physical swing can reach this handler more than once through the
        // event plumbing (bus bridge, combat pipeline re-fires). Since the same
        // victim cannot legitimately be directly hit twice inside
        // DUPLICATE_HIT_WINDOW_MS, treat a repeat as a duplicate and count it
        // once — otherwise the charge rune skips a hit.
        long now = System.currentTimeMillis();
        Long lastCounted = recentVictimHits.get(livingVictim.getUniqueId());
        if (lastCounted != null && now - lastCounted < DUPLICATE_HIT_WINDOW_MS)
            return;
        recentVictimHits.put(livingVictim.getUniqueId(), now);
        pruneRecentVictimHits(now);

        // STATE 2: The blade is currently transformed into a Diamond Sword
        if (held.getType() == Material.DIAMOND_SWORD) {
            if (releasing)
                return; // charge already consumed this window; wave is reverting
            // Execute the cleave on the 3rd hit (1st hit while transformed)
            spawnCleave(player, livingVictim, holder);
            // Reset state & revert blade back to Iron
            consumeMorph(player);
            return;
        }

        // STATE 1: The blade is in its normal Iron Sword form
        if (held.getType() == Material.IRON_SWORD) {
            hitCount++;
            if (hitCount >= CLEAVE_INTERVAL) { // 2 Hits reached
                hitCount = 0;
                morphToDiamond(player);
            }
        }
    }

    /**
     * Consume the charged state right after a cleave: cancel the long revert
     * timer and schedule a short cosmetic delay before swapping back to iron,
     * so the released wave becomes visible while the blade is still diamond.
     * (An immediate swap made the sword flip to iron *before* the wave rendered
     * on the following tick, which looked like the cleave firing after the
     * material had already reverted.)
     */
    private void consumeMorph(Player player) {
        cancelPendingRevert();
        hitCount = 0;

        // Fall back to main hand if morphedSlot was not set properly
        final int slot = (morphedSlot != Integer.MIN_VALUE) ? morphedSlot : player.getInventory().getHeldItemSlot();
        morphedSlot = Integer.MIN_VALUE;

        Plugin p = resolvePlugin();
        if (p == null) {
            releasing = false;
            return;
        }
        cancelPendingConsume();
        final UUID uuid = player.getUniqueId();
        pendingConsume = Bukkit.getScheduler().runTaskLater(p, () -> {
            pendingConsume = null;
            Player pl = Bukkit.getPlayer(uuid);
            if (pl == null || !pl.isOnline()) {
                releasing = false;
                return;
            }
            finishRelease(pl, slot);
        }, RELEASE_REVERT_DELAY_TICKS);
    }

    private void finishRelease(Player pl, int slot) {
        releasing = false;
        if (revertToIron(pl, slot)) {
            pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.05f);
            pl.getWorld().spawnParticle(Particle.SMOKE, pl.getLocation().clone().add(0, 1, 0), 6, 0.2, 0.3, 0.2,
                    0.02);
        }
    }

    /** Bounds the dedupe map; melee victims churn quickly. */
    private void pruneRecentVictimHits(long now) {
        if (recentVictimHits.size() < 32)
            return;
        recentVictimHits.entrySet().removeIf(e -> now - e.getValue() > 5000L);
    }

    private static boolean isArcaneBlade(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        return ITEM_ID.equals(id);
    }

    private static Plugin resolvePlugin() {
        try {
            return DMain.getInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private void morphToDiamond(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean isMain = isArcaneBlade(held);
        ItemStack target = held;
        int slot = player.getInventory().getHeldItemSlot();
        if (!isMain) {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (isArcaneBlade(off)) {
                target = off;
                slot = -1;
            } else {
                return;
            }
        }
        if (target.getType() == Material.DIAMOND_SWORD)
            return; // already charged; its revert timer governs
        ItemStack diamond = copyWithMaterial(target, Material.DIAMOND_SWORD);
        if (diamond == null)
            return;
        if (slot == -1)
            player.getInventory().setItemInOffHand(diamond);
        else
            player.getInventory().setItemInMainHand(diamond);
        morphedSlot = slot;
        World w = player.getWorld();
        w.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.7f);
        w.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.2f);
        w.spawnParticle(Particle.ENCHANTED_HIT, player.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.1);
        scheduleRevert(player, slot);
    }

    private void scheduleRevert(Player player, int slot) {
        Plugin p = resolvePlugin();
        if (p == null)
            return;
        UUID uuid = player.getUniqueId();
        pendingRevert = Bukkit.getScheduler().runTaskLater(p, () -> {
            pendingRevert = null;
            Player pl = Bukkit.getPlayer(uuid);
            if (pl == null || !pl.isOnline())
                return;
            if (revertToIron(pl, slot)) {
                pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.05f);
                pl.getWorld().spawnParticle(Particle.SMOKE, pl.getLocation().clone().add(0, 1, 0), 6, 0.2, 0.3, 0.2,
                        0.02);
            }
        }, DIAMOND_DURATION_TICKS);
    }

    /**
     * Swap a diamond-morphed arcane blade back to its iron form. Returns false when
     * no morphed blade is found in the recorded hand or — after a hotbar switch —
     * its original hotbar slot.
     */
    private static boolean revertToIron(Player pl, int slot) {
        boolean success = false;

        // Check main hand or designated slot
        if (slot == -1) { // Offhand
            ItemStack off = pl.getInventory().getItemInOffHand();
            if (isArcaneBlade(off) && off.getType() == Material.DIAMOND_SWORD) {
                pl.getInventory().setItemInOffHand(copyWithMaterial(off, Material.IRON_SWORD));
                success = true;
            }
        } else { // Main hand or specific hotbar slot
            int actualSlot = (slot >= 0 && slot <= 8) ? slot : pl.getInventory().getHeldItemSlot();
            ItemStack current = pl.getInventory().getItem(actualSlot);

            if (isArcaneBlade(current) && current.getType() == Material.DIAMOND_SWORD) {
                pl.getInventory().setItem(actualSlot, copyWithMaterial(current, Material.IRON_SWORD));
                success = true;
            } else {
                // Fallback check on current main hand if player switched slots
                ItemStack mainHand = pl.getInventory().getItemInMainHand();
                if (isArcaneBlade(mainHand) && mainHand.getType() == Material.DIAMOND_SWORD) {
                    pl.getInventory().setItemInMainHand(copyWithMaterial(mainHand, Material.IRON_SWORD));
                    success = true;
                }
            }
        }

        if (success) {
            // Force inventory update to prevent visual/event desync on the same tick
            pl.updateInventory();
        }

        return success;
    }

    private static ItemStack copyWithMaterial(ItemStack original, Material newMaterial) {
        if (original == null || newMaterial == null)
            return null;
        ItemStack copy = original.clone();
        ItemMeta meta = copy.getItemMeta();
        ItemStack next = new ItemStack(newMaterial, copy.getAmount());
        if (meta != null)
            next.setItemMeta(meta);
        return next;
    }

    // ---- Cleave ----

    private void spawnCleave(Player player, LivingEntity originVictim, RPGEntity attackerRpg) {
        final World world = originVictim.getWorld();
        if (world == null)
            return;
        final Location origin = originVictim.getLocation().clone().add(0, player.getHeight() * 0.5, 0);
        Vector fwdTmp = player.getEyeLocation().getDirection().clone();
        final Vector flatForward;
        {
            Vector tmp = fwdTmp.clone();
            tmp.setY(0);
            if (tmp.lengthSquared() < 0.0001)
                tmp = new Vector(0, 0, 1);
            tmp.normalize();
            flatForward = tmp;
        }
        final Vector right;
        {
            Vector r = new Vector(-flatForward.getZ(), 0, flatForward.getX());
            r.normalize();
            right = r;
        }
        long now = System.currentTimeMillis();
        double attackDamage = attackerRpg.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now);
        double cleaveDmg = attackDamage * CLEAVE_DAMAGE_MULTIPLIER;
        if (cleaveDmg < 1.0)
            cleaveDmg = 10.0;
        final double finalCleaveDamage = cleaveDmg;

        world.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.25f);
        world.playSound(origin, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.6f);
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.3f);
        world.spawnParticle(Particle.SWEEP_ATTACK, origin.clone().add(flatForward.clone().multiply(0.6)), 1, 0, 0, 0,
                0);
        world.spawnParticle(Particle.END_ROD, origin, 8, 0.3, 0.3, 0.3, 0.05);

        final Set<UUID> alreadyHit = ConcurrentHashMap.newKeySet();
        alreadyHit.add(player.getUniqueId());
        alreadyHit.add(originVictim.getUniqueId());

        final Plugin p = resolvePlugin();
        if (p == null)
            return;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= CLEAVE_TICKS) {
                    cancel();
                    return;
                }
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                World tickWorld = player.getWorld();
                if (tickWorld == null || !origin.getWorld().equals(tickWorld))
                    tickWorld = world;
                final World w = tickWorld;
                Location center = origin.clone().add(flatForward.clone().multiply(ticks * CLEAVE_FORWARD_STEP));
                Location centerVis = center.clone().add(0, 0.25, 0);
                for (double deg = -90; deg <= 90.001; deg += 15) {
                    double rad = Math.toRadians(deg);
                    double cos = Math.cos(rad);
                    double sin = Math.sin(rad);
                    Vector offset = right.clone().multiply(sin * CLEAVE_RADIUS)
                            .add(flatForward.clone().multiply(cos * CLEAVE_RADIUS));
                    Location pLoc = centerVis.clone().add(offset);
                    w.spawnParticle(Particle.ENCHANTED_HIT, pLoc, 1, 0.02, 0.02, 0.02, 0.01);
                    w.spawnParticle(Particle.CRIT, pLoc, 1, 0.02, 0.02, 0.02, 0.15);
                    w.spawnParticle(Particle.DUST, pLoc, 1, 0.02, 0.02, 0.02, 0,
                            new Particle.DustOptions(Color.fromRGB(0x8A2BE2), 1.1f));
                    if (Math.abs(deg) < 0.1)
                        w.spawnParticle(Particle.WITCH, pLoc, 2, 0.08, 0.08, 0.08, 0.02);
                }
                w.spawnParticle(Particle.SWEEP_ATTACK, centerVis.clone().add(0, -0.1, 0), 1, 0, 0, 0, 0);
                if (ticks % 2 == 0)
                    w.spawnParticle(Particle.DUST, centerVis, 3, 0.15, 0.08, 0.15, 0,
                            new Particle.DustOptions(Color.fromRGB(0x5500AA), 1.0f));
                double searchRadius = CLEAVE_RADIUS + 0.7;
                for (Entity e : w.getNearbyEntities(center, searchRadius, CLEAVE_Y_TOLERANCE, searchRadius)) {
                    if (!(e instanceof LivingEntity le))
                        continue;
                    UUID eid = le.getUniqueId();
                    if (alreadyHit.contains(eid))
                        continue;
                    if (le.isDead() || !le.isValid())
                        continue;
                    if (EntityManager.getInstance().isGhost(eid))
                        continue;
                    if (!CombatRelation.isEnemy(attackerRpg, e))
                        continue;
                    double dy = Math.abs(le.getLocation().getY() - center.getY());
                    if (dy > CLEAVE_Y_TOLERANCE)
                        continue;
                    Vector offset = le.getLocation().toVector().subtract(center.toVector());
                    offset.setY(0);
                    if (!inHalfCircle(flatForward, offset, searchRadius))
                        continue;
                    alreadyHit.add(eid);
                    java.util.Optional<RPGEntity> opt = EntityManager.getInstance().getEntity(eid);
                    if (opt.isPresent()) {
                        RPGEntity targetRpg = opt.get();
                        RPGDamageResult res = targetRpg.dealRPGDamage(attackerRpg, targetRpg, finalCleaveDamage,
                                DamageType.MAGIC);
                        if (res.getResult() != DamageResult.DENY) {
                            DMain inst = DMain.getInstance();
                            CombatListener cl = inst == null ? null : inst.getCombatListener();
                            if (cl != null)
                                cl.showMagicDamage(le.getLocation(), res.getDamage(), res.getResult());
                            w.spawnParticle(Particle.CRIT, le.getLocation().clone().add(0, le.getHeight() * 0.5, 0), 14,
                                    0.3, 0.4, 0.3, 0.18);
                            w.spawnParticle(Particle.ENCHANTED_HIT, le.getEyeLocation(), 8, 0.25, 0.25, 0.25, 0.12);
                            w.playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.2f);
                            Vector kb = offset.clone().normalize();
                            if (kb.lengthSquared() < 0.001)
                                kb = flatForward.clone();
                            kb.multiply(0.35);
                            kb.setY(0.22);
                            le.setVelocity(kb);
                        }
                    } else {
                        DamageUtils.damageMob(le, finalCleaveDamage, player);
                        w.spawnParticle(Particle.CRIT, le.getLocation().clone().add(0, le.getHeight() * 0.5, 0), 10,
                                0.3, 0.4, 0.3, 0.16);
                        w.playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.1f);
                        Vector kb = offset.clone().normalize().multiply(0.32);
                        kb.setY(0.18);
                        le.setVelocity(kb);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(p, 0L, 1L);
    }

    public static boolean inHalfCircle(Vector forward, Vector offset, double radius) {
        double dx = offset.getX();
        double dz = offset.getZ();
        double distSq = dx * dx + dz * dz;
        if (distSq > radius * radius)
            return false;
        if (distSq == 0)
            return true;
        double invDist = 1.0 / Math.sqrt(distSq);
        double dot = (dx * forward.getX() + dz * forward.getZ()) * invDist;
        return dot >= 0;
    }

    public static List<Vector> computeArcPoints(Vector center, Vector forward, Vector right, double radius) {
        List<Vector> points = new ArrayList<>();
        for (double deg = -90; deg <= 90.001; deg += 15) {
            double rad = Math.toRadians(deg);
            Vector off = right.clone().multiply(Math.sin(rad) * radius)
                    .add(forward.clone().multiply(Math.cos(rad) * radius));
            points.add(center.clone().add(off));
        }
        return points;
    }

    // For tests / external reset
    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int c) {
        hitCount = c;
    }
}
