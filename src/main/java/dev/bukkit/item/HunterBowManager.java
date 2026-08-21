package dev.bukkit.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.HunterHudFormatter;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;

/**
 * Hunter's Bow runtime — the Sova-inspired bow for the Archer class.
 * <p>
 * Two staged modifiers feed the next shot:
 * <ul>
 *   <li><b>Bouncy Arrows</b> — Shift while the bow is equipped cycles the next
 *       arrow's bounce charges {@code 0 -> 1 -> 2 -> 3 -> 0}. Feedback: rising
 *       pling, END_ROD burst, action-bar dots.</li>
 *   <li><b>Shock Bolt</b> — Left-click arms the next arrow with an explosive
 *       payload. It detonates after all bounces are exhausted, or on first
 *       impact when fired uncharged.</li>
 * </ul>
 * Charged / armed arrows carry PDC markers ({@link #ARROW_BOUNCES_KEY},
 * {@link #ARROW_EXPLOSIVE_KEY}) plus the existing {@link BowArrowManager#BOUNCE_KEY}
 * recoverability flag, a coloured DUST trail and — for shock bolts — a
 * glowing outline. Bounce physics mirrors the user-supplied reflection snippet
 * (restitution + friction, anti-clip offset).
 * <p>
 * Now wired through the project's {@link EventSubscriber} / {@link Subscribe}
 * bus (see {@link dev.bukkit.event.bukkitListeners.EventBusRegistry}) instead
 * of a vanilla {@code Listener}. {@link EntityShootBowEvent},
 * {@link ProjectileHitEvent}, {@link PlayerQuitEvent} and
 * {@link PlayerDeathEvent} are forwarded to the bus and handled here at
 * {@link EventAction#HIGHEST_PRIORITY} where ordering matters.
 */
@EventSubscriber
public class HunterBowManager {

    public static final String ITEM_ID = "HUNTERS_BOW";
    public static final int MAX_BOUNCES = 3;

    public static final NamespacedKey ARROW_BOUNCES_KEY = new NamespacedKey("project_d", "hunter_bounces");
    public static final NamespacedKey ARROW_EXPLOSIVE_KEY = new NamespacedKey("project_d", "hunter_explosive");

    // ---- Physics ----
    private static final double BOUNCINESS = 0.65;
    private static final double FRICTION = 0.85;
    private static final float BOUNCE_MIN_SPEED = 0.35f;
    private static final float BOUNCE_MAX_SPEED = 3.0f;
    private static final float BOUNCE_SPREAD = 2.0f;

    // ---- Explosion ----
    private static final double EXPLOSION_RADIUS = 3.5;
    private static final double EXPLOSION_MULTIPLIER = 1.5;

    private static final Map<UUID, Integer> CHARGED_BOUNCES = new HashMap<>();
    private static final Map<UUID, Boolean> EXPLOSIVE_ARMED = new HashMap<>();

    private static HunterBowManager instance;

    private static Plugin plugin;
    private static boolean running;
    private static BukkitTask heldPollTask;

    public HunterBowManager() {
        if (instance == null) {
            instance = this;
        }
    }

    public HunterBowManager(Plugin plugin) {
        HunterBowManager.plugin = plugin;
        HunterBowManager.running = true;
        instance = this;
    }

    public static HunterBowManager getInstance() {
        if (instance == null) {
            instance = new HunterBowManager();
        }
        return instance;
    }

    // ---- Lifecycle ----

    /**
     * Stores the plugin reference for scheduler tasks. Event wiring is now
     * handled by {@link EventSubscriber} discovery, so no Bukkit
     * {@code registerEvents} call is made here.
     */
    public void start(Plugin plugin) {
        HunterBowManager.plugin = plugin;
        HunterBowManager.running = true;
        if (instance == null || instance != this) {
            instance = this;
        }
        startHeldPoll();
    }

    public void stop() {
        if (!running) {
            // still clear state even if not marked running — covers the scanner-created instance
        }
        running = false;
        CHARGED_BOUNCES.clear();
        EXPLOSIVE_ARMED.clear();
        stopHeldPoll();
        // clear HUD for all tracked players
        try {
            for (Player p : Bukkit.getOnlinePlayers()) {
                hideHud(p);
            }
        } catch (Exception ignored) {}
    }

    private void startHeldPoll() {
        stopHeldPoll();
        if (plugin == null) return;
        heldPollTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    boolean holds = isHunterBow(p.getInventory().getItemInMainHand());
                    if (holds) {
                        refreshHud(p);
                    } else {
                        // hide hunter HUD if not holding (idempotent)
                        if (hasHunterHud(p)) {
                            hideHud(p);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }, 20L, 5L);
    }

    private void stopHeldPoll() {
        if (heldPollTask != null) {
            try { heldPollTask.cancel(); } catch (Exception ignored) {}
            heldPollTask = null;
        }
    }

    private boolean hasHunterHud(Player player) {
        if (player == null) return false;
        try {
            return HudOverlayService.getInstance().getActiveCount(player) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void cycleBounceCharges(Player player) {
        if (player == null || !isHunterBow(player.getInventory().getItemInMainHand())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int current = CHARGED_BOUNCES.getOrDefault(uuid, 0);
        int next = (current + 1) % (MAX_BOUNCES + 1);
        if (next == 0) {
            CHARGED_BOUNCES.remove(uuid);
        } else {
            CHARGED_BOUNCES.put(uuid, next);
        }

        Location loc = player.getLocation();
        World world = player.getWorld();

        // Rising pitch per level — level 0 wraps with a soft bass thud.
        if (next == 0) {
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.75f);
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f);
        } else {
            float pitch = 0.75f + next * 0.28f; // 1.03 / 1.31 / 1.59
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
            world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.1f + next * 0.18f);
        }

        // Recon-style burst — cyan END_ROD ring + electric sparks
//        Location eye = player.getEyeLocation();
//        Color burstColor = trailColor(next, false);
//        Particle.DustOptions dust = new Particle.DustOptions(burstColor, 1.2f);
//        world.spawnParticle(Particle.DUST, eye.clone().add(0, -0.25, 0), 10, 0.25, 0.25, 0.25, 0, dust);
//        world.spawnParticle(Particle.END_ROD, eye.clone().add(0, -0.15, 0), 8, 0.3, 0.3, 0.3, 0.02);
//        world.spawnParticle(Particle.ELECTRIC_SPARK, eye.clone().add(0, -0.15, 0), 14, 0.4, 0.4, 0.4, 0.06);

        refreshHud(player);
    }

    /**
     * Toggle the player's next-shot shock charge. Armed state persists until
     * consumed by the next hunter-bow shot (or death/quit).
     */
    public void toggleExplosiveArrows(Player player) {
        if (player == null || !isHunterBow(player.getInventory().getItemInMainHand())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean currentlyArmed = Boolean.TRUE.equals(EXPLOSIVE_ARMED.get(uuid));
        boolean nextArmed = !currentlyArmed;
        if (nextArmed) {
            EXPLOSIVE_ARMED.put(uuid, true);
        } else {
            EXPLOSIVE_ARMED.remove(uuid);
        }

        Location loc = player.getLocation();
        World world = player.getWorld();

        if (nextArmed) {
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

        refreshHud(player);
    }

    // ---- Shoot stamping (bus subscriber) ----

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null) {
            bow = player.getInventory().getItemInMainHand();
        }
        if (!isHunterBow(bow)) {
            return;
        }

        int bounces = CHARGED_BOUNCES.getOrDefault(player.getUniqueId(), 0);
        boolean explosive = Boolean.TRUE.equals(EXPLOSIVE_ARMED.get(player.getUniqueId()));

        if (bounces == 0 && !explosive) {
            return; // plain shot — vanilla bookkeeping via BowArrowManager
        }

        // Consume staged modifiers
        CHARGED_BOUNCES.remove(player.getUniqueId());
        EXPLOSIVE_ARMED.remove(player.getUniqueId());

        // Stamp PDC — bounces remaining, shock flag, recoverability
        var pdc = arrow.getPersistentDataContainer();
        pdc.set(ARROW_BOUNCES_KEY, PersistentDataType.INTEGER, bounces);
        pdc.set(ARROW_EXPLOSIVE_KEY, PersistentDataType.BOOLEAN, explosive);
        pdc.set(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN, true);

        if (explosive) {
            arrow.setGlowing(true);
        }

        // Shoot feedback (arrow already has ARROW_DAMAGE_KEY stamped via BowArrowManager's
        // deferred task; trail visual is the main differentiator).
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

        // Refresh HUD to reflect consumed state (now 0/plain); shot trail still armed
        refreshHud(player);

        // Arm the in-flight trail
        startTrail(arrow, bounces, explosive);
    }

    // ---- Projectile impact (bus subscriber) ----

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        Integer bouncesLeft = arrow.getPersistentDataContainer().get(ARROW_BOUNCES_KEY, PersistentDataType.INTEGER);
        if (bouncesLeft == null) {
            return; // not a hunter-bow arrow
        }
        boolean explosive = Boolean.TRUE.equals(
                arrow.getPersistentDataContainer().get(ARROW_EXPLOSIVE_KEY, PersistentDataType.BOOLEAN));

        // Entity hit — only shock bolts react (AoE on the struck target).
        if (event.getHitEntity() != null) {
            if (explosive) {
                RPGEntity shooter = resolveShooter(arrow);
                if (shooter != null) {
                    event.setCancelled(true);
                    Location hitLoc = arrow.getLocation().clone();
                    Double stored = arrow.getPersistentDataContainer()
                            .get(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE);
                    arrow.remove();
                    detonate(hitLoc, shooter, stored);
                }
            }
            return;
        }

        // Block hit
        if (event.getHitBlock() == null) {
            return;
        }
        BlockFace face = event.getHitBlockFace();
        if (face == null) {
            return;
        }

        if (bouncesLeft > 0) {
            event.setCancelled(true);
            bounceArrow(arrow, face, bouncesLeft - 1, explosive);
        } else if (explosive) {
            RPGEntity shooter = resolveShooter(arrow);
            if (shooter != null) {
                event.setCancelled(true);
                Location hitLoc = arrow.getLocation().clone();
                Double stored = arrow.getPersistentDataContainer()
                        .get(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE);
                arrow.remove();
                detonate(hitLoc, shooter, stored);
            }
        }
    }

    @Subscribe
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        CHARGED_BOUNCES.remove(uuid);
        EXPLOSIVE_ARMED.remove(uuid);
        hideHud(event.getPlayer());
    }

    @Subscribe
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            CHARGED_BOUNCES.remove(uuid);
            EXPLOSIVE_ARMED.remove(uuid);
            hideHud(player);
        }
    }


    @Subscribe
    public void onHeldChange(PlayerItemHeldEvent event) {
        if (plugin == null) return;
        // event fires before client updates, defer one tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = event.getPlayer();
            try {
                ItemStack newItem = p.getInventory().getItem(event.getNewSlot());
                if (isHunterBow(newItem)) {
                    refreshHud(p);
                } else {
                    hideHud(p);
                }
            } catch (Exception ignored) {}
        });
    }

    @Subscribe
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = event.getPlayer();
            try {
                if (isHunterBow(p.getInventory().getItemInMainHand())) {
                    refreshHud(p);
                } else {
                    hideHud(p);
                }
            } catch (Exception ignored) {}
        });
    }

    @Subscribe
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (plugin == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (isHunterBow(p.getInventory().getItemInMainHand())) {
                    refreshHud(p);
                } else {
                    hideHud(p);
                }
            } catch (Exception ignored) {}
        });
    }

    @Subscribe
    public void onJoinHudInit(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        // hide existing overlays from the new viewer (MVP privacy)
        try {
            HudOverlayService.getInstance().hideAllFrom(joined);
        } catch (Exception ignored) {}
        // if the joiner spawns holding the bow, the poll will show shortly; also try immediate
        if (plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (isHunterBow(joined.getInventory().getItemInMainHand())) {
                        refreshHud(joined);
                    }
                } catch (Exception ignored) {}
            }, 5L);
        }
    }

    // ---- Bounce physics (inspiration snippet) ----

    private void bounceArrow(Arrow oldArrow, BlockFace face, int remainingBounces, boolean explosive) {
        Vector hitNormal = face.getDirection();
        Vector incomingVelocity = oldArrow.getVelocity();

        if (incomingVelocity.lengthSquared() < 0.01) {
            incomingVelocity = oldArrow.getLocation().getDirection().normalize();
            if (incomingVelocity.lengthSquared() < 0.001) {
                incomingVelocity = hitNormal.clone();
            }
        }

        double dot = incomingVelocity.dot(hitNormal);
        Vector normalComponent = hitNormal.clone().multiply(dot);
        Vector tangentialComponent = incomingVelocity.clone().subtract(normalComponent);

        Vector reflectedVelocity = tangentialComponent.multiply(FRICTION)
                .subtract(normalComponent.multiply(BOUNCINESS));

        float speed = (float) reflectedVelocity.length();
        if (speed < BOUNCE_MIN_SPEED) {
            speed = BOUNCE_MIN_SPEED;
        } else if (speed > BOUNCE_MAX_SPEED) {
            speed = BOUNCE_MAX_SPEED;
        }
        Vector direction = reflectedVelocity.clone().normalize();
        // Guard against a degenerate zero vector after friction
        if (direction.lengthSquared() < 0.001) {
            direction = hitNormal.clone();
        }

        Location spawnLoc = oldArrow.getLocation().clone().add(hitNormal.clone().multiply(0.2));
        World world = spawnLoc.getWorld();
        if (world == null) {
            oldArrow.remove();
            return;
        }

        Double storedDamage = oldArrow.getPersistentDataContainer()
                .get(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE);

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
        if (storedDamage != null) {
            pdc.set(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, storedDamage);
        }

        // Impact FX — recon-bolt bounce ping
        world.spawnParticle(Particle.ELECTRIC_SPARK, spawnLoc, 10, 0.14, 0.14, 0.14, 0.07);
        world.spawnParticle(Particle.END_ROD, spawnLoc, 5, 0.18, 0.18, 0.18, 0.02);
        world.spawnParticle(Particle.DUST, spawnLoc, 8, 0.18, 0.18, 0.18, 0,
                new Particle.DustOptions(trailColor(remainingBounces + 1, explosive), 1.1f));
        world.playSound(spawnLoc, Sound.ENTITY_ARROW_HIT_PLAYER, 0.45f, 0.72f);
        world.playSound(spawnLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65f, 1.85f);

        startTrail(newArrow, remainingBounces, explosive);
        oldArrow.remove();
    }

    // ---- Explosion (shock bolt) ----

    private void detonate(Location loc, RPGEntity shooter, Double storedDamage) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        // Sova shock-bolt burst: emitter + electric storm + hot dust + flash
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

        if (storedDamage == null || storedDamage <= 0) {
            return;
        }

        LivingEntity shooterEntity = resolveEntity(shooter);
        double baseDamage = storedDamage * EXPLOSION_MULTIPLIER * shooter.getAbilityDamageMultiplier();

        for (Entity entity : world.getNearbyEntities(loc, EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS)) {
            if (!(entity instanceof LivingEntity le)) {
                continue;
            }
            if (le.getUniqueId().equals(shooter.getUuid())) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(le.getUniqueId())) {
                continue;
            }
            if (!CombatRelation.isEnemy(shooter, entity)) {
                continue;
            }
            // Same distance check the bat uses — spherical radius already, but ensure falloff cap
            double dist = le.getLocation().distance(loc);
            if (dist > EXPLOSION_RADIUS) {
                continue;
            }
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

    // ---- Trail ----

    private void startTrail(Arrow arrow, int bounces, boolean explosive) {
        if (plugin == null) {
            return;
        }
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
                        if (lived % 6 == 0) {
                            w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.08, 0.08, 0.08, 0.02);
                        }
                    } else {
                        if (lived % 4 == 0) {
                            w.spawnParticle(Particle.END_ROD, l, 1, 0.04, 0.04, 0.04, 0.005);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Color trailColor(int bounces, boolean explosive) {
        if (explosive) {
            // Shock charge tints the trail hot orange; bouncy+shock is a brighter amber.
            return bounces > 0 ? Color.fromRGB(0xFF9800) : Color.fromRGB(0xFF5252);
        }
        return switch (bounces) {
            case 3 -> Color.fromRGB(0x18FFFF); // bright aqua — Sova recon
            case 2 -> Color.fromRGB(0x40C4FF); // light blue
            case 1 -> Color.fromRGB(0xB388FF); // violet
            default -> Color.fromRGB(0x78909C); // fallback grey (shouldn't trail)
        };
    }

    // ---- HUD (stacked TextDisplay, persistent while held) ----

    private static void refreshHud(Player player) {
        if (player == null) return;
        if (!isHunterBow(player.getInventory().getItemInMainHand())) {
            hideHud(player);
            return;
        }
        int bounces = CHARGED_BOUNCES.getOrDefault(player.getUniqueId(), 0);
        boolean armed = Boolean.TRUE.equals(EXPLOSIVE_ARMED.get(player.getUniqueId()));
        HudOverlayService hud = HudOverlayService.getInstance();
        // bounce bottom (priority 10), shock top (priority 20) — stacked 0.28 apart
        hud.show(player, "hunter:bounce", HunterHudFormatter.formatBounce(bounces), 0, 10);
        hud.show(player, "hunter:shock", HunterHudFormatter.formatShock(armed), 0, 20);
    }

    private static void hideHud(Player player) {
        if (player == null) return;
        HudOverlayService hud = HudOverlayService.getInstance();
        hud.hide(player, "hunter:bounce");
        hud.hide(player, "hunter:shock");
        hud.hide(player, "hunter:shot");
    }

    // ---- Helpers ----

    private static boolean isHunterBow(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        return ITEM_ID.equals(id);
    }

    private RPGEntity resolveShooter(Arrow arrow) {
        if (!(arrow.getShooter() instanceof Player shooterPlayer)) {
            return null;
        }
        return EntityManager.getInstance().getEntity(shooterPlayer.getUniqueId()).orElse(null);
    }

    private static LivingEntity resolveEntity(RPGEntity caster) {
        if (caster instanceof dev.bukkit.entity.BukkitPlayerEntity pe) {
            return pe.getPlayer().orElse(null);
        }
        if (Bukkit.getServer() == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(caster.getUuid());
        return e instanceof LivingEntity le ? le : null;
    }

    private void knockback(LivingEntity le, Location blastLoc) {
        Vector dir = le.getLocation().toVector().subtract(blastLoc.toVector()).normalize();
        // Slight upward lift sells the blast without launching dungeon mobs into the void
        dir.setY(Math.max(dir.getY(), 0.15));
        le.setVelocity(dir.multiply(0.42));
    }

    private void showPhysicalIndicator(LivingEntity le, double damage, DamageResult result) {
        if (damage <= 0) {
            return;
        }
        DMain inst = DMain.getInstance();
        CombatListener cl = inst == null ? null : inst.getCombatListener();
        if (cl == null) {
            return;
        }
        cl.showPhysicalDamage(le.getLocation(), damage, result);
    }

    private void playHitSound(LivingEntity le, DamageResult result) {
        DMain inst = DMain.getInstance();
        CombatListener cl = inst == null ? null : inst.getCombatListener();
        if (cl == null) {
            return;
        }
        cl.playProjectileHitSound(le, result);
    }
}
