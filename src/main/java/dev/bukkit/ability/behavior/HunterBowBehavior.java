package dev.bukkit.ability.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.HunterHudFormatter;
import dev.bukkit.item.BowArrowManager;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.ability.ActiveAbilityRegistry;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;

/**
 * Per-holder Hunter Bow behavior — consolidates bouncy + explosive logic.
 * Bounce charges / explosive arming live in per-holder instance state tracked
 * via {@link ActiveAbilityRegistry}. One behavior instance per holder (shared
 * between BOUNCY_ARROWS and EXPLOSIVE_ARROWS abilities via holder cache).
 *
 * <p>The toggled state (bounce charges + shock bolt arm) deliberately survives
 * item swaps — only logging out resets it; there is no configured duration, so
 * it stays armed until fired, toggled off or quit.</p>
 */
public class HunterBowBehavior implements AbilityBehavior {

    public static final String ITEM_ID = "HUNTERS_BOW";
    public static final int MAX_BOUNCES = 3;
    public static final NamespacedKey ARROW_BOUNCES_KEY = new NamespacedKey("project_d", "hunter_bounces");
    public static final NamespacedKey ARROW_EXPLOSIVE_KEY = new NamespacedKey("project_d", "hunter_explosive");
    private static final double BOUNCINESS = 0.65;
    private static final double FRICTION = 0.85;
    private static final float BOUNCE_MIN_SPEED = 0.35f;
    private static final float BOUNCE_MAX_SPEED = 3.0f;
    private static final float BOUNCE_SPREAD = 2.0f;
    private static final double EXPLOSION_RADIUS = 3.5;
    private static final double EXPLOSION_MULTIPLIER = 1.5;

    private static final class HolderState {
        int bounceCharges = 0;
        boolean explosiveArmed = false;
    }

    private static final Map<UUID, HolderState> HOLDER_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> HOLDER_REFCNT = new ConcurrentHashMap<>();

    private ActiveAbility ctx;

    private HolderState state() {
        return HOLDER_STATE.computeIfAbsent(ctx.getHolder().getUuid(), k -> new HolderState());
    }

    public HunterBowBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
        HOLDER_STATE.computeIfAbsent(ctx.getHolder().getUuid(), k -> new HolderState());
        HOLDER_REFCNT.merge(ctx.getHolder().getUuid(), 1, Integer::sum);
    }

    public static HunterBowBehavior forHolder(UUID uuid) {
        // legacy accessor kept for compat; returns null now that state is in
        // HOLDER_STATE
        return null;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        HOLDER_STATE.computeIfAbsent(ctx.getHolder().getUuid(), k -> new HolderState());
        boolean isFirst = HOLDER_REFCNT.getOrDefault(ctx.getHolder().getUuid(), 0) == 1;
        if (isFirst) {
            ctx.getSubscriptions().subscribe(
                    new EventAction<>(this::onShoot, EntityShootBowEvent.class, EventAction.HIGHEST_PRIORITY));
            ctx.getSubscriptions()
                    .subscribe(new EventAction<>(this::onHit, ProjectileHitEvent.class, EventAction.HIGHEST_PRIORITY));
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onQuit, PlayerQuitEvent.class));
        }
        refreshHudForHolder();
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        UUID uuid = ctx.getHolder().getUuid();
        int cnt = HOLDER_REFCNT.getOrDefault(uuid, 1) - 1;
        if (cnt <= 0) {
            HOLDER_REFCNT.remove(uuid);
            // HOLDER_STATE intentionally kept: toggled state (bounce charges +
            // shock bolt) outlives unequip until quit or consumption.
            hideHudForHolder();
        } else {
            HOLDER_REFCNT.put(uuid, cnt);
        }
    }

    // ---- Ability entry points (called from effects) ----

    public void cycleBounceCharges(Player player) {
        if (player == null || !isHunterBow(player.getInventory().getItemInMainHand()))
            return;
        HolderState s = state();
        s.bounceCharges = (s.bounceCharges + 1) % (MAX_BOUNCES + 1);
        Location loc = player.getLocation();
        World world = player.getWorld();
        if (s.bounceCharges == 0) {
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f);
        } else {
            float pitch = 0.75f + s.bounceCharges * 0.28f;
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
            world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.1f + s.bounceCharges * 0.18f);
        }
        refreshHudForHolder();
    }

    public void toggleExplosive(Player player) {
        if (player == null || !isHunterBow(player.getInventory().getItemInMainHand()))
            return;
        HolderState s = state();
        s.explosiveArmed = !s.explosiveArmed;
        Location loc = player.getLocation();
        World world = player.getWorld();
        if (s.explosiveArmed) {
            world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 0.65f, 1.45f);
            world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.6f);
            Location burstAt = player.getEyeLocation().add(0, -0.3, 0);
            world.spawnParticle(Particle.SMALL_FLAME, burstAt, 18, 0.45, 0.3, 0.45, 0.02);
            world.spawnParticle(Particle.ELECTRIC_SPARK, burstAt, 10, 0.45, 0.35, 0.45, 0.07);
            world.spawnParticle(Particle.DUST, burstAt, 10, 0.35, 0.25, 0.35, 0,
                    new Particle.DustOptions(Color.fromRGB(0xFF6D00), 1.35f));
            world.spawnParticle(Particle.FLASH, burstAt, 1);
        } else {
            world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.55f, 0.9f);
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.7f);
            world.spawnParticle(Particle.SMOKE, player.getEyeLocation().add(0, -0.3, 0), 12, 0.35, 0.3, 0.35, 0.02);
        }
        refreshHudForHolder();
    }

    // ---- Shoot stamping ----

    private void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (!player.getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        if (!(event.getProjectile() instanceof Arrow arrow))
            return;
        ItemStack bow = event.getBow();
        if (bow == null)
            bow = player.getInventory().getItemInMainHand();
        if (!isHunterBow(bow))
            return;
        HolderState s = state();
        if (s.bounceCharges == 0 && !s.explosiveArmed)
            return;

        // consume
        int bounces = s.bounceCharges;
        boolean explosive = s.explosiveArmed;
        s.bounceCharges = 0;
        s.explosiveArmed = false;

        var pdc = arrow.getPersistentDataContainer();
        pdc.set(ARROW_BOUNCES_KEY, PersistentDataType.INTEGER, bounces);
        pdc.set(ARROW_EXPLOSIVE_KEY, PersistentDataType.BOOLEAN, explosive);
        pdc.set(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN, true);
        if (explosive)
            arrow.setGlowing(true);

        Location loc = player.getLocation();
        World world = player.getWorld();
        if (bounces > 0) {
            world.playSound(loc, Sound.ENTITY_ARROW_SHOOT, 0.85f, 1.55f);
            world.spawnParticle(Particle.ELECTRIC_SPARK, player.getEyeLocation(), 6, 0.18, 0.18, 0.18, 0.05);
        }
        if (explosive) {
            world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.45f, 1.35f);
            world.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.8f);
        }
        refreshHudForHolder();
        startTrail(arrow, bounces, explosive);
    }

    private void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow))
            return;
        if (arrow.getShooter() instanceof Player p && !p.getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        Integer bouncesLeft = arrow.getPersistentDataContainer().get(ARROW_BOUNCES_KEY, PersistentDataType.INTEGER);
        if (bouncesLeft == null)
            return;
        boolean explosive = Boolean.TRUE
                .equals(arrow.getPersistentDataContainer().get(ARROW_EXPLOSIVE_KEY, PersistentDataType.BOOLEAN));
        if (event.getHitEntity() != null) {
            if (explosive) {
                RPGEntity shooter = resolveShooter(arrow);
                if (shooter != null) {
                    event.setCancelled(true);
                    Location hitLoc = arrow.getLocation().clone();
                    Double stored = arrow.getPersistentDataContainer().get(BowArrowManager.ARROW_DAMAGE_KEY,
                            PersistentDataType.DOUBLE);
                    arrow.remove();
                    detonate(hitLoc, shooter, stored);
                }
            }
            return;
        }
        if (event.getHitBlock() == null)
            return;
        BlockFace face = event.getHitBlockFace();
        if (face == null)
            return;
        if (bouncesLeft > 0) {
            event.setCancelled(true);
            bounceArrow(arrow, face, bouncesLeft - 1, explosive);
        } else if (explosive) {
            RPGEntity shooter = resolveShooter(arrow);
            if (shooter != null) {
                event.setCancelled(true);
                Location hitLoc = arrow.getLocation().clone();
                Double stored = arrow.getPersistentDataContainer().get(BowArrowManager.ARROW_DAMAGE_KEY,
                        PersistentDataType.DOUBLE);
                arrow.remove();
                detonate(hitLoc, shooter, stored);
            }
        }
    }

    private void onQuit(PlayerQuitEvent e) {
        if (!e.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        // session end: toggled state does not survive relogin
        HOLDER_STATE.remove(ctx.getHolder().getUuid());
        hideHudForHolder();
    }

    /**
     * Deferred by one tick via {@link BehaviorScheduler}: item-swap events fire
     * before the new main-hand item is applied, so an immediate held-item check
     * would hide the freshly activated HUD instead of showing it.
     */
    private void refreshHudForHolder() {
        UUID uuid = ctx.getHolder().getUuid();
        BehaviorScheduler.runNextTick(() -> {
            try {
                if (!HOLDER_REFCNT.containsKey(uuid))
                    return; // deactivated before the task ran
                Player p = Bukkit.getPlayer(uuid);
                if (p == null)
                    return;
                if (!isHunterBow(p.getInventory().getItemInMainHand())) {
                    hideHudKeys(p);
                    return;
                }
                HolderState s = HOLDER_STATE.get(uuid);
                int bc = s == null ? 0 : s.bounceCharges;
                boolean ea = s != null && s.explosiveArmed;
                HudOverlayService hud = HudOverlayService.getInstance();
                hud.show(p, "hunter:bounce", HunterHudFormatter.formatBounce(bc), 0, 10);
                hud.show(p, "hunter:shock", HunterHudFormatter.formatShock(ea), 0, 20);
            } catch (Exception ignored) {
            }
        });
    }

    private void hideHudForHolder() {
        try {
            Player p = Bukkit.getPlayer(ctx.getHolder().getUuid());
            if (p != null)
                hideHudKeys(p);
        } catch (Exception ignored) {
        }
    }

    private static void hideHudKeys(Player p) {
        HudOverlayService.getInstance().hide(p, "hunter:bounce");
        HudOverlayService.getInstance().hide(p, "hunter:shock");
    }

    private void bounceArrow(Arrow oldArrow, BlockFace face, int remainingBounces, boolean explosive) {
        Vector hitNormal = face.getDirection();
        Vector incomingVelocity = oldArrow.getVelocity();
        if (incomingVelocity.lengthSquared() < 0.01) {
            incomingVelocity = oldArrow.getLocation().getDirection().normalize();
            if (incomingVelocity.lengthSquared() < 0.001)
                incomingVelocity = hitNormal.clone();
        }
        double dot = incomingVelocity.dot(hitNormal);
        Vector normalComponent = hitNormal.clone().multiply(dot);
        Vector tangentialComponent = incomingVelocity.clone().subtract(normalComponent);
        Vector reflectedVelocity = tangentialComponent.multiply(FRICTION)
                .subtract(normalComponent.multiply(BOUNCINESS));
        float speed = (float) reflectedVelocity.length();
        if (speed < BOUNCE_MIN_SPEED)
            speed = BOUNCE_MIN_SPEED;
        else if (speed > BOUNCE_MAX_SPEED)
            speed = BOUNCE_MAX_SPEED;
        Vector direction = reflectedVelocity.clone().normalize();
        if (direction.lengthSquared() < 0.001)
            direction = hitNormal.clone();
        Location spawnLoc = oldArrow.getLocation().clone().add(hitNormal.clone().multiply(0.2));
        World world = spawnLoc.getWorld();
        if (world == null) {
            oldArrow.remove();
            return;
        }
        Double storedDamage = oldArrow.getPersistentDataContainer().get(BowArrowManager.ARROW_DAMAGE_KEY,
                PersistentDataType.DOUBLE);
        Arrow newArrow = world.spawnArrow(spawnLoc, direction, speed, BOUNCE_SPREAD);
        newArrow.setGlowing(oldArrow.isGlowing());
        newArrow.setFireTicks(oldArrow.getFireTicks());
        newArrow.setShooter(oldArrow.getShooter());
        newArrow.setCritical(oldArrow.isCritical());
        newArrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        var pdc = newArrow.getPersistentDataContainer();
        pdc.set(ARROW_BOUNCES_KEY, PersistentDataType.INTEGER, remainingBounces);
        pdc.set(ARROW_EXPLOSIVE_KEY, PersistentDataType.BOOLEAN, explosive);
        pdc.set(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN, true);
        if (storedDamage != null)
            pdc.set(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, storedDamage);
        world.spawnParticle(Particle.ELECTRIC_SPARK, spawnLoc, 10, 0.14, 0.14, 0.14, 0.07);
        world.spawnParticle(Particle.END_ROD, spawnLoc, 5, 0.18, 0.18, 0.18, 0.02);
        world.spawnParticle(Particle.DUST, spawnLoc, 8, 0.18, 0.18, 0.18, 0,
                new Particle.DustOptions(trailColor(remainingBounces + 1, explosive), 1.1f));
        world.playSound(spawnLoc, Sound.ENTITY_ARROW_HIT_PLAYER, 0.45f, 0.72f);
        world.playSound(spawnLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65f, 1.85f);
        startTrail(newArrow, remainingBounces, explosive);
        oldArrow.remove();
    }

    private void detonate(Location loc, RPGEntity shooter, Double storedDamage) {
        World world = loc.getWorld();
        if (world == null)
            return;
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 55, 2.2, 1.2, 2.2, 0.18);
        world.spawnParticle(Particle.DUST, loc, 38, 1.5, 0.85, 1.5, 0,
                new Particle.DustOptions(Color.fromRGB(0xFF6D00), 1.55f));
        world.spawnParticle(Particle.DUST, loc, 22, 1.1, 0.65, 1.1, 0,
                new Particle.DustOptions(Color.fromRGB(0xFF3D00), 1.2f));
        world.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.92f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 1.25f);
        world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.6f);
        if (storedDamage == null || storedDamage <= 0)
            return;
        LivingEntity shooterEntity = resolveEntity(shooter);
        double baseDamage = storedDamage * EXPLOSION_MULTIPLIER * shooter.getAbilityDamageMultiplier();
        for (Entity entity : world.getNearbyEntities(loc, EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS)) {
            if (!(entity instanceof LivingEntity le))
                continue;
            if (le.getUniqueId().equals(shooter.getUuid()))
                continue;
            if (EntityManager.getInstance().isGhost(le.getUniqueId()))
                continue;
            if (!CombatRelation.isEnemy(shooter, entity))
                continue;
            double dist = le.getLocation().distance(loc);
            if (dist > EXPLOSION_RADIUS)
                continue;
            double falloff = 1.0 - (0.5 * dist / EXPLOSION_RADIUS);
            double damage = baseDamage * falloff;
            EntityManager.getInstance().getEntity(entity.getUniqueId()).ifPresentOrElse(target -> {
                RPGDamageResult result = target.dealRPGDamage(shooter, target, damage, DamageType.PHYSICAL);
                if (result.getResult() != DamageResult.DENY) {
                    knockback(le, loc);
                    showPhysicalIndicator(le, result.getDamage(), result.getResult());
                    playHitSound(le, result.getResult());
                }
            }, () -> {
                DamageUtils.damageMob(le, damage, shooterEntity);
                knockback(le, loc);
                playHitSound(le, DamageResult.NORMAL);
            });
        }
    }

    private void startTrail(Arrow arrow, int bounces, boolean explosive) {
        Plugin plugin = DMain.getInstance();
        if (plugin == null)
            return;
        Particle.DustOptions dust = new Particle.DustOptions(trailColor(bounces, explosive), 1.35f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                Location l = arrow.getLocation();
                World w = l.getWorld();
                if (w == null) {
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.DUST, l, 2, 0.02, 0.02, 0.02, 0, dust);
                int lived = arrow.getTicksLived();
                if (lived % 2 == 0) {
                    if (explosive) {
                        w.spawnParticle(Particle.SMALL_FLAME, l, 1, 0.02, 0.02, 0.02, 0.005);
                        if (lived % 6 == 0)
                            w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.08, 0.08, 0.08, 0.02);
                    } else {
                        if (lived % 4 == 0)
                            w.spawnParticle(Particle.END_ROD, l, 1, 0.04, 0.04, 0.04, 0.005);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Color trailColor(int bounces, boolean explosive) {
        if (explosive)
            return bounces > 0 ? Color.fromRGB(0xFF9800) : Color.fromRGB(0xFF5252);
        return switch (bounces) {
        case 3 -> Color.fromRGB(0x18FFFF);
        case 2 -> Color.fromRGB(0x40C4FF);
        case 1 -> Color.fromRGB(0xB388FF);
        default -> Color.fromRGB(0x78909C);
        };
    }

    private static boolean isHunterBow(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        return ITEM_ID.equals(id);
    }

    private RPGEntity resolveShooter(Arrow arrow) {
        if (!(arrow.getShooter() instanceof Player p))
            return null;
        return EntityManager.getInstance().getEntity(p.getUniqueId()).orElse(null);
    }

    private static LivingEntity resolveEntity(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity pe)
            return pe.getPlayer().orElse(null);
        if (Bukkit.getServer() == null)
            return null;
        Entity e = Bukkit.getEntity(caster.getUuid());
        return e instanceof LivingEntity le ? le : null;
    }

    private void knockback(LivingEntity le, Location blastLoc) {
        Vector dir = le.getLocation().toVector().subtract(blastLoc.toVector()).normalize();
        dir.setY(Math.max(dir.getY(), 0.15));
        le.setVelocity(dir.multiply(0.42));
    }

    private void showPhysicalIndicator(LivingEntity le, double damage, DamageResult result) {
        if (damage <= 0)
            return;
        DMain inst = DMain.getInstance();
        dev.bukkit.event.bukkitListeners.CombatListener cl = inst == null ? null : inst.getCombatListener();
        if (cl == null)
            return;
        cl.showPhysicalDamage(le.getLocation(), damage, result);
    }

    private void playHitSound(LivingEntity le, DamageResult result) {
        DMain inst = DMain.getInstance();
        dev.bukkit.event.bukkitListeners.CombatListener cl = inst == null ? null : inst.getCombatListener();
        if (cl == null)
            return;
        cl.playProjectileHitSound(le, result);
    }

    public int getBounceCharges() {
        HolderState s = HOLDER_STATE.get(ctx.getHolder().getUuid());
        return s == null ? 0 : s.bounceCharges;
    }

    public boolean isExplosiveArmed() {
        HolderState s = HOLDER_STATE.get(ctx.getHolder().getUuid());
        return s != null && s.explosiveArmed;
    }
}
