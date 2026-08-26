package dev.bukkit.ability.behavior;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import dev.bukkit.DMain;
import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.summon.SoulSkull;
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
 * Per-holder behavior for the Shadow Weaver's Staff. Owns every piece of the
 * staff runtime that runs while the assassin merely holds the item: the raycast
 * placement preview, the platform lifecycle (spawn, decay, despawn), the dash
 * target lock, the interpolated dash animation and the sticky "float on the
 * platform" lock. Click actions are delivered by the ability pipeline (see
 * BukkitShadowWeaverPlaceEffect and BukkitShadowWeaverDashEffect) which forward
 * here through the per-holder behavior.
 *
 * <p>
 * State is mapped by player UUID and kept entirely server-side; the only
 * entities spawned are {@link BlockDisplay} platforms and a single preview
 * marker per player. Real Minecraft blocks are never placed, so entities pass
 * straight through the visual platforms. Platforms persist for their full
 * lifetime no matter how the player leaves them: only the 6-second duration (or
 * the 3-platform cap) removes them, so the assassin can hop freely between old
 * and new platforms.
 *
 * <p>
 * One behavior instance per holder (shared between the PLACE and DASH abilities
 * via {@link #HOLDER_CACHE}); a per-holder 1-tick loop starts on first
 * activation. Unequipping the staff releases locks/preview but lets placed
 * platforms fade out gracefully — the loop keeps advancing their decay until
 * none remain. On server shutdown, state is disposed immediately.
 */
public class ShadowWeaverBehavior implements AbilityBehavior {

    /** Item id the staff behaviors bind to (see items.yml). */
    public static final String ITEM_ID = "SHADOW_WEAVER_STAFF";

    // ---- Raycast / placement ------------------------------------------------
    /** Maximum raycast range (blocks) for the placement preview. */
    public static final int RAYCAST_MAX_BLOCKS = 5;
    /** Minimum separation between platforms (blocks), squared. */
    public static final double MIN_PLATFORM_DISTANCE_SQ = 2.0;
    /** Maximum number of platforms a single player may keep active. */
    public static final int MAX_PLATFORMS = 3;

    // ---- Platform lifecycle ---------------------------------------------------
    /** Total lifetime of a platform in milliseconds. */
    public static final long PLATFORM_LIFETIME_MS = 6000;
    /**
     * Colour palette stops for the green -> red decay, at fifths of the lifetime.
     */
    public static final long DECAY_LIME_MS = PLATFORM_LIFETIME_MS / 5;
    public static final long DECAY_YELLOW_MS = PLATFORM_LIFETIME_MS * 2 / 5;
    public static final long DECAY_ORANGE_MS = PLATFORM_LIFETIME_MS * 3 / 5;
    public static final long DECAY_RED_MS = PLATFORM_LIFETIME_MS * 4 / 5;

    // ---- Dash / target lock ---------------------------------------------------
    /** Minimum dash range in blocks. */
    public static final double DASH_RANGE_MIN = 0.5;
    /** Maximum dash range in blocks. */
    public static final double DASH_RANGE_MAX = 20.0;
    /** Dot-product threshold used to confirm the crosshair is on a platform. */
    public static final double AIM_DOT_THRESHOLD = 0.995;
    /** Duration of the dash animation in ticks (0.2s). */
    public static final int DASH_TICKS = 4;

    // ---- Assassin synergies ---------------------------------------------------
    /** Duration of the Invisibility + Slow Falling granted on platform drop-off. */
    public static final long BUFF_DURATION_MS = 1500;
    /** Duration of the Plunge Strike damage window after leaving a platform. */
    public static final long PLUNGE_WINDOW_MS = 3000;
    /** Melee damage multiplier while Plunge Strike is armed. */
    public static final double PLUNGE_MULTIPLIER = 1.5;

    // ---- Dash-through damage --------------------------------------------------
    /** Ratio of attack damage dealt when dashing through an enemy. */
    public static final double DASH_DAMAGE_AD_RATIO = 0.6;
    /** Ratio of lethality dealt when dashing through an enemy. */
    public static final double DASH_DAMAGE_LETHALITY_RATIO = 0.3;
    /** Radius around the dash segment considered a "through" hit. */
    public static final double DASH_DAMAGE_RADIUS = 1.5;

    /**
     * Horizontal displacement (blocks) from the snap point that counts as a manual
     * WASD drop.
     */
    private static final double DROP_MOVE_THRESHOLD = 0.2;

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> TICK_TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> HOLDER_REFCNT = new ConcurrentHashMap<>();
    // Holder-scoped cache so PLACE and DASH abilities share the same behavior per
    // player
    private static final Map<UUID, ShadowWeaverBehavior> HOLDER_CACHE = new ConcurrentHashMap<>();

    private ActiveAbility ctx;

    public ShadowWeaverBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
        HOLDER_CACHE.put(ctx.getHolder().getUuid(), this);
        HOLDER_REFCNT.merge(ctx.getHolder().getUuid(), 1, Integer::sum);
        STATES.computeIfAbsent(ctx.getHolder().getUuid(), k -> new PlayerState());
    }

    public static ShadowWeaverBehavior forHolder(UUID uuid) {
        return HOLDER_CACHE.get(uuid);
    }

    // ---- Test hooks (statics live for the whole JVM) -------------------------

    static int refCountForTest(UUID uuid) {
        return HOLDER_REFCNT.getOrDefault(uuid, 0);
    }

    static boolean hasStateForTest(UUID uuid) {
        return STATES.containsKey(uuid);
    }

    static boolean hasTickTaskForTest(UUID uuid) {
        return TICK_TASKS.containsKey(uuid);
    }

    /** Clears all per-holder state; tests must call this between scenarios. */
    static void resetForTest() {
        STATES.clear();
        TICK_TASKS.clear();
        HOLDER_REFCNT.clear();
        HOLDER_CACHE.clear();
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        UUID uuid = ctx.getHolder().getUuid();
        HOLDER_CACHE.put(uuid, this);
        STATES.computeIfAbsent(uuid, k -> new PlayerState());
        boolean isFirst = HOLDER_REFCNT.getOrDefault(uuid, 0) == 1;
        if (isFirst) {
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onMove, PlayerMoveEvent.class));
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onSneak, PlayerToggleSneakEvent.class));
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onQuit, PlayerQuitEvent.class));
            ctx.getSubscriptions().subscribe(new EventAction<>(this::onDeath, PlayerDeathEvent.class));
        }
        // Always make sure the render loop is running while the staff is bound;
        // startTickTask dedupes via TICK_TASKS, so this is a no-op when the
        // first activation already scheduled it.
        startTickTask(uuid);
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
        PlayerState state = STATES.get(uuid);
        if (state == null) {
            cancelTickTask(uuid);
            return;
        }
        Plugin plugin = resolvePlugin();
        Player player = Bukkit.getPlayer(uuid);
        if (plugin != null && !plugin.isEnabled()) {
            // Server shutting down: dispose immediately (parity with the
            // legacy manager's stop() teardown).
            if (player != null) {
                state.releaseLock(player);
            }
            state.disposeAll();
            STATES.remove(uuid);
            cancelTickTask(uuid);
            return;
        }
        // Graceful fade: drop the sticky lock and preview now, but leave the
        // platforms in place. The per-holder tick keeps advancing their decay
        // until none remain (see tickHolder).
        if (player != null) {
            state.releaseLock(player);
        }
        state.hideIndicator();
        state.clearLockOn();
    }

    private void startTickTask(UUID uuid) {
        if (TICK_TASKS.containsKey(uuid)) {
            return;
        }
        Plugin plugin = resolvePlugin();
        if (plugin == null) {
            return; // headless (tests): no scheduler available
        }
        try {
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    tickHolder(uuid);
                }
            }.runTaskTimer(plugin, 0L, 1L);
            TICK_TASKS.put(uuid, task);
        } catch (Exception ignored) {
            // Server shutting down or scheduling unavailable
        }
    }

    private void cancelTickTask(UUID uuid) {
        BukkitTask task = TICK_TASKS.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // ============================================================== PLAYER CLICKS

    /** Right-click placement action, invoked by the place effect. */
    public void handlePlace(Player player) {
        if (!player.getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }
        if (!HOLDER_REFCNT.containsKey(player.getUniqueId())) {
            return; // torn down: don't spawn a platform with no render loop
        }
        PlayerState state = state(player.getUniqueId());
        if (state.isDashing()) {
            return;
        }
        // Recompute the current crosshair target so the placement is exact even
        // if the preview ticked a moment ago.
        Location eye = player.getEyeLocation();
        Location target = raycastTarget(eye, eye.getDirection().normalize(), RAYCAST_MAX_BLOCKS);
        if (target == null || !isValidPlacement(player, state, target)) {
            return;
        }
        state.target = target;
        spawnPlatform(player, state, target);
    }

    /** Left-click dash action, invoked by the dash effect. */
    public void handleDash(Player player) {
        if (!player.getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }
        if (!HOLDER_REFCNT.containsKey(player.getUniqueId())) {
            return; // torn down: no live lock-on can exist
        }
        PlayerState state = state(player.getUniqueId());
        if (state.isDashing()) {
            return;
        }
        Platform target = state.lockedOn;
        if (target == null || target.isInvalid()) {
            return;
        }
        startDash(player, state, target);
    }

    // ============================================================== PER-TICK LOOP

    private void tickHolder(UUID uuid) {
        PlayerState state = STATES.get(uuid);
        if (state == null) {
            if (!HOLDER_REFCNT.containsKey(uuid)) {
                // Torn down and nothing left to drain: stop ticking.
                cancelTickTask(uuid);
                return;
            }
            // Still bound but the runtime vanished (missed-teardown ordering):
            // rebuild it instead of killing the render loop.
            state = STATES.computeIfAbsent(uuid, k -> new PlayerState());
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!holdsStaff(player)) {
            // Put the staff away: hide the preview, release any lock/dash
            // and let the platforms fade out on their own.
            state.releaseLock(player);
            state.hideIndicator();
            state.clearLockOn();
        } else {
            tickState(player, state, now);
        }
        // Platform decay always advances so platforms never linger forever,
        // even after the staff is sheathed or unequipped.
        tickPlatforms(player, state, now);
        // Graceful-fade completion: once deactivated and no platforms remain,
        // drop the state and stop the per-holder task.
        if (!HOLDER_REFCNT.containsKey(uuid) && state.platforms.isEmpty()) {
            STATES.remove(uuid);
            cancelTickTask(uuid);
        }
    }

    private void tickState(Player player, PlayerState state, long now) {
        if (state.isDashing()) {
            advanceDash(player, state);
        } else if (state.isLocked() && shouldDrop(player, state)) {
            releasePlatform(player, state);
        }

        updateIndicator(player, state);
        updateTargetLock(player, state, now);
        // Platform decay is advanced for every tracked player in tickHolder.
    }

    // ------------------------------------------------------------- preview

    private void updateIndicator(Player player, PlayerState state) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location target = raycastTarget(eye, dir, RAYCAST_MAX_BLOCKS);
        if (target == null) {
            state.hideIndicator();
            state.target = null;
            return;
        }

        boolean valid = isValidPlacement(player, state, target);
        state.showIndicator(target, valid);
        state.target = target;
    }

    // ============================================================== PLUNGE STRIKE

    /**
     * Consumes the assassin's armed Plunge Strike: the first melee hit within 3s of
     * leaving a platform deals {@value #PLUNGE_MULTIPLIER}x damage. Returns the
     * multiplier (1.0 when not armed) and clears the window once used.
     */
    public static double consumePlungeMultiplier(UUID attackerUuid) {
        PlayerState state = STATES.get(attackerUuid);
        if (state == null) {
            return 1.0;
        }
        if (System.currentTimeMillis() > state.plungeExpiresAt) {
            return 1.0;
        }
        state.plungeExpiresAt = 0;
        return PLUNGE_MULTIPLIER;
    }

    // ------------------------------------------------------------- placement
    // helpers

    /**
     * Steps along the eye ray until a non-passable block is met, returning the last
     * passable block location (or the ray's endpoint when the whole ray is clear).
     * Null when the ray immediately starts inside solid geometry.
     */
    private static Location raycastTarget(Location eye, Vector dir, int maxBlocks) {
        World world = eye.getWorld();
        // Raytrace collidable block surfaces
        RayTraceResult result = world.rayTraceBlocks(eye, dir, maxBlocks, FluidCollisionMode.NEVER, true);

        if (result != null && result.getHitPosition() != null) {
            return result.getHitPosition().toLocation(world);
        }

        return eye.clone().add(dir.clone().multiply(maxBlocks));
    }

    /**
     * A placement is valid when its target block is air, in range and further than
     * the minimum separation from every active platform.
     */
    private static boolean isValidPlacement(Player player, PlayerState state, Location target) {
        if (!target.getBlock().isPassable()) {
            return false;
        }
        int rangeSquared = (RAYCAST_MAX_BLOCKS + 1) * (RAYCAST_MAX_BLOCKS + 1);
        if (player.getEyeLocation().distanceSquared(target) > rangeSquared) {
            return false;
        }
        Vector targetVec = target.toVector();
        for (Platform platform : state.platforms) {
            if (platform.isInvalid()) {
                continue;
            }
            if (targetVec.distanceSquared(platform.location.toVector()) < MIN_PLATFORM_DISTANCE_SQ) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- platforms

    private void spawnPlatform(Player player, PlayerState state, Location target) {
        if (!isValidPlacement(player, state, target)) {
            return;
        }
        // Enforce the 3-platform cap: the oldest platform despawns.
        if (state.platforms.size() >= MAX_PLATFORMS) {
            Platform oldest = state.platforms.remove(0);
            if (oldest != null) {
                despawn(oldest);
            }
        }
        Location center = target.clone();

        // Scaled, flattened core display that glows brilliantly inside the particle
        // cloud
        BlockDisplay display = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(center, BlockDisplay.class,
                d -> {
                    d.setBlock(Material.PEARLESCENT_FROGLIGHT.createBlockData());
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setGlowing(true);
                    d.setGlowColorOverride(Color.fromRGB(0xFF, 0xD7, 0x00)); // Golden aura
                    d.setTeleportDuration(1);
                    d.setTransformation(new Transformation(new Vector3f(-0.35f, -0.05f, -0.35f), identityAxis(),
                            new Vector3f(0.7f, 0.1f, 0.7f), identityAxis()));
                });
        Platform platform = new Platform(display, center, System.currentTimeMillis());
        state.platforms.add(platform);
    }

    /**
     * Advances platform decay, updates glowing color transitions, and spawns the
     * energetic cloud swirl particles around each platform.
     */
    private void tickPlatforms(Player player, PlayerState state, long now) {
        Iterator<Platform> it = state.platforms.iterator();
        while (it.hasNext()) {
            Platform platform = it.next();
            if (platform.isInvalid()) {
                it.remove();
                continue;
            }
            platform.display.teleport(platform.location);

            long age = now - platform.spawnedAt;
            Color currentColor = platformColor(age);

            // Dynamically match the inner glow color to the platform's decay lifecycle
            platform.display.setGlowColorOverride(currentColor);

            // Render the dense swirling cloud effect
            spawnCloudParticles(platform, currentColor);

            if (age >= PLATFORM_LIFETIME_MS) {
                it.remove();
                despawn(platform);
                if (state.lockedPlatform == platform) {
                    state.releaseLock(player);
                }
                if (state.lockedOn == platform) {
                    state.clearLockOn();
                }
            }
        }
    }

    /** Despawns a platform with an exploding puff of energy particles. */
    private static void despawn(Platform platform) {
        if (platform.isInvalid()) {
            return;
        }
        World world = platform.location.getWorld();
        world.spawnParticle(Particle.FIREWORK, platform.location.clone().add(0, 0.2, 0), 12, 0.3, 0.1, 0.3, 0.05);
        world.spawnParticle(Particle.CLOUD, platform.location.clone().add(0, 0.2, 0), 6, 0.2, 0.1, 0.2, 0.02);
        platform.display.remove();
    }

    /**
     * Spawns an animated, swirling cloud mist around the platform center using
     * orbiting orbits of dust, flame, and energy rods.
     */
    private static void spawnCloudParticles(Platform platform, Color color) {
        World world = platform.location.getWorld();
        if (world == null)
            return;

        Location center = platform.location.clone();
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.25f);

        // Core ambient energy mist
        world.spawnParticle(Particle.END_ROD, center, 1, 0.2, 0.05, 0.2, 0.01);
        world.spawnParticle(Particle.CLOUD, center, 1, 0.25, 0.02, 0.25, 0.005);

        // Dynamic dual-ring spiral swirl
        long time = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            double angle = (time / 180.0) + (i * (Math.PI / 3));
            double radius = 0.65 + (Math.sin(angle * 2) * 0.1);

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = (Math.sin(angle * 3) * 0.08);

            Location particleLoc = center.clone().add(x, y, z);

            world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.SMALL_FLAME, particleLoc, 1, 0.01, 0.01, 0.01, 0.005);
        }
    }

    // ------------------------------------------------------------- dash lock

    private void updateTargetLock(Player player, PlayerState state, long now) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Platform best = null;
        double bestDot = -2;
        for (Platform platform : state.platforms) {
            if (platform.isInvalid()) {
                continue;
            }
            Vector toPlatform = platform.location.toVector().subtract(eye.toVector());
            double distSq = toPlatform.lengthSquared();
            if (!insideDashRange(distSq)) {
                continue;
            }
            double dot = toPlatform.normalize().dot(dir);
            if (dot > bestDot) {
                bestDot = dot;
                best = platform;
            }
        }

        if (best != null && bestDot >= AIM_DOT_THRESHOLD) {
            lockOn(player, state, best, now);
        } else if (state.lockedOn != null) {
            state.clearLockOn();
        }
    }

    /**
     * Highlights a platform, plays the lock chime once and rings it with soul-fire
     * while the crosshair stays on it.
     */
    private void lockOn(Player player, PlayerState state, Platform platform, long now) {
        if (state.lockedOn != platform) {
            state.lockedOn = platform;
            state.lockedOnAt = now;
            platform.display.setGlowing(true);
            platform.display.setGlowColorOverride(Color.fromRGB(0x8A2BE2));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, 1.6f);
        }
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, platform.location.clone().add(0, 0.35, 0), 2, 0.35,
                0.35, 0.35, 0.0);
    }

    private void startDash(Player player, PlayerState state, Platform target) {
        Location from = player.getLocation().clone();
        Location to = target.location.clone();
        to.setYaw(player.getLocation().getYaw());
        to.setPitch(player.getLocation().getPitch());

        // Ability chain: dashing to another platform is seamless. The old
        // platform is NOT despawned - it keeps decaying and stays available for
        // a later dash - and no gravity drop / drop-off buff occurs (the chain
        // is not a "departure").
        if (state.isLocked()) {
            state.lockedPlatform = null;
            state.lockedAt = null;
            player.setGravity(true);
        }

        state.dashing = true;
        state.dashTick = 0;
        state.dashStart = from;
        state.dashEnd = to;
        state.dashTarget = target;
        state.dashHitEntities.clear();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 0.4f, 0.9f);
    }

    private void advanceDash(Player player, PlayerState state) {
        Location previous = player.getLocation().clone();
        state.dashTick++;
        Location from = state.dashStart;
        Location to = state.dashEnd;
        if (state.dashTick >= DASH_TICKS) {
            // Arrival: snap onto the diagonal platform, float and cancel any
            // vertical velocity so the assassin never slips off.
            Location arrival = to.clone();
            arrival.setYaw(player.getLocation().getYaw());
            arrival.setPitch(player.getLocation().getPitch());
            player.teleport(arrival);
            handleDashThroughDamage(player, state, previous, arrival);
            player.setGravity(false);
            player.setVelocity(new Vector());
            state.dashing = false;
            state.lockedPlatform = state.dashTarget;
            state.lockedAt = arrival.clone();
            return;
        }
        // Step-by-step interpolated teleport that retains the player's look.
        double progress = dashStep(state.dashTick, DASH_TICKS);
        double x = from.getX() + (to.getX() - from.getX()) * progress;
        double y = from.getY() + (to.getY() - from.getY()) * progress;
        double z = from.getZ() + (to.getZ() - from.getZ()) * progress;
        Location step = new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(),
                player.getLocation().getPitch());
        player.teleport(step);
        handleDashThroughDamage(player, state, previous, step);
        player.getWorld().spawnParticle(Particle.SMOKE, step, 3, 0.1, 0.1, 0.1, 0.02);
    }

    /**
     * Deals dash-through damage to every enemy whose hitbox the player's dash
     * segment passes through. Raw damage is
     * {@code 0.6 * ATTACK_DAMAGE + 0.3 * LETHALITY} as physical damage. Each enemy
     * is hit at most once per dash.
     */
    private void handleDashThroughDamage(Player player, PlayerState state, Location segmentStart, Location segmentEnd) {
        if (segmentStart == null || segmentEnd == null || segmentStart.getWorld() == null
                || segmentEnd.getWorld() == null) {
            return;
        }
        if (!segmentStart.getWorld().equals(segmentEnd.getWorld())) {
            return;
        }
        World world = segmentStart.getWorld();
        var attackerOpt = EntityManager.getInstance().getEntity(player.getUniqueId());
        if (attackerOpt.isEmpty()) {
            return;
        }
        RPGEntity attacker = attackerOpt.get();
        long now = System.currentTimeMillis();
        double attackDamage = attacker.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now);
        double lethality = attacker.getStatEngineAdapter().getCurrentValue(StatType.LETHALITY, now);
        double rawDamage = calculateDashThroughDamage(attackDamage, lethality);
        if (rawDamage <= 0.001) {
            return;
        }
        Vector midVec = segmentStart.toVector().add(segmentEnd.toVector()).multiply(0.5);
        Location mid = new Location(world, midVec.getX(), midVec.getY(), midVec.getZ());
        double segLength = segmentStart.distance(segmentEnd);
        double searchRadius = segLength / 2.0 + DASH_DAMAGE_RADIUS + 0.5;
        searchRadius = Math.max(searchRadius, DASH_DAMAGE_RADIUS + 0.5);
        List<Entity> candidates = new ArrayList<>(
                world.getNearbyEntities(mid, searchRadius, searchRadius, searchRadius));
        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity le)) {
                continue;
            }
            if (le.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (le.isDead() || !le.isValid()) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(le.getUniqueId())) {
                continue;
            }
            if (SoulSkull.isSoulSkull(le)) {
                continue;
            }
            if (state.dashHitEntities.contains(le.getUniqueId())) {
                continue;
            }
            if (!CombatRelation.isEnemy(attacker, le)) {
                continue;
            }
            Location entityCenter = le.getLocation().clone().add(0, le.getHeight() * 0.5, 0);
            double distSq = distanceToSegmentSquared(entityCenter, segmentStart, segmentEnd);
            // Fallback to feet distance as well: some mobs have base at feet
            double feetDistSq = distanceToSegmentSquared(le.getLocation(), segmentStart, segmentEnd);
            double bestDistSq = Math.min(distSq, feetDistSq);
            double hitRadius = DASH_DAMAGE_RADIUS + 0.5;
            if (bestDistSq > hitRadius * hitRadius) {
                continue;
            }
            state.dashHitEntities.add(le.getUniqueId());
            EntityManager.getInstance().getEntity(le.getUniqueId()).ifPresentOrElse(targetRpg -> {
                RPGDamageResult res = targetRpg.dealRPGDamage(attacker, targetRpg, rawDamage, DamageType.PHYSICAL);
                if (res.getResult() != DamageResult.DENY) {
                    try {
                        CombatListener cl = DMain.getInstance() != null ? DMain.getInstance().getCombatListener()
                                : null;
                        if (cl != null) {
                            cl.showPhysicalDamage(le.getLocation(), res.getDamage(), res.getResult());
                        }
                    } catch (Exception ignored) {
                    }
                    world.spawnParticle(Particle.CRIT, entityCenter, 8, 0.2, 0.2, 0.2, 0.1);
                    world.playSound(entityCenter, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.1f);
                }
            }, () -> {
                DamageUtils.damageMob(le, rawDamage, player);
                world.spawnParticle(Particle.CRIT, entityCenter, 8, 0.2, 0.2, 0.2, 0.1);
                world.playSound(entityCenter, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.1f);
            });
        }
    }

    // ------------------------------------------------------------- manual drop

    /** Whether the player's own input (WASD, jump or sneak) broke the stick. */
    private boolean shouldDrop(Player player, PlayerState state) {
        if (player.isSneaking()) {
            return true;
        }
        Location snap = state.lockedAt;
        if (snap == null) {
            return false;
        }
        Vector delta = player.getLocation().toVector().subtract(snap.toVector());
        delta.setY(0);
        return delta.lengthSquared() > DROP_MOVE_THRESHOLD * DROP_MOVE_THRESHOLD;
    }

    /**
     * Restores gravity and arms the assassin's drop-off synergies. The platform
     * itself is left in place: it keeps decaying and can still be dashed to until
     * its lifetime fully elapses.
     */
    private void releasePlatform(Player player, PlayerState state) {
        Platform platform = state.lockedPlatform;
        state.lockedPlatform = null;
        state.lockedAt = null;
        player.setGravity(true);
        player.setVelocity(new Vector());
        if (state.lockedOn == platform) {
            state.clearLockOn();
        }
        applyDepartureBuff(player);
    }

    /**
     * Grants 1.5s of Invisibility + Slow Falling and arms the 3s Plunge Strike
     * window. Called on manual drops and non-chain dashes only.
     */
    private void applyDepartureBuff(Player player) {
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, (int) (BUFF_DURATION_MS / 50), 0, false, false));
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOW_FALLING, (int) (BUFF_DURATION_MS / 50), 0, false, false));
        state(player.getUniqueId()).plungeExpiresAt = System.currentTimeMillis() + PLUNGE_WINDOW_MS;
    }

    // ============================================================== STATE ACCESS

    private PlayerState state(UUID uuid) {
        return STATES.computeIfAbsent(uuid, key -> new PlayerState());
    }

    private boolean holdsStaff(Player player) {
        return ITEM_ID.equals(BukkitItemStackAdapter.getRpgItemId(player.getInventory().getItemInMainHand()));
    }

    private static Plugin resolvePlugin() {
        try {
            return DMain.getInstance();
        } catch (Exception e) {
            return null;
        }
    }

    // ============================================================== EVENTS

    private void onMove(PlayerMoveEvent event) {
        if (!event.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }
        PlayerState state = STATES.get(event.getPlayer().getUniqueId());
        if (state != null && state.isLocked() && shouldDrop(event.getPlayer(), state)) {
            releasePlatform(event.getPlayer(), state);
        }
    }

    private void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        if (!event.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }
        PlayerState state = STATES.get(event.getPlayer().getUniqueId());
        if (state != null && state.isLocked()) {
            releasePlatform(event.getPlayer(), state);
        }
    }

    private void onQuit(PlayerQuitEvent event) {
        if (!event.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }
        cleanupPlayer(event.getPlayer());
    }

    private void onDeath(PlayerDeathEvent event) {
        if (!event.getEntity().getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }
        cleanupPlayer(event.getEntity());
    }

    /**
     * Full per-holder session teardown (quit/death). Every step is idempotent: a
     * zombie generation firing this again after a rejoin must not disturb the live
     * generation's state, refcount or tick task.
     */
    private void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerState state = STATES.remove(uuid);
        if (state != null) {
            state.releaseLock(player);
            state.disposeAll();
        }
        cancelTickTask(uuid);
        // Drop the shared-generation bookkeeping so the next equip session
        // starts from refcount 1 and re-arms its subscriptions + tick task
        // (see onActivate). Without this the stale count survives relogin and
        // permanently disables the staff runtime.
        HOLDER_REFCNT.remove(uuid);
        HOLDER_CACHE.remove(uuid);
        ctx.getSubscriptions().unsubscribeAll();
    }

    // ============================================================== PURE LOGIC

    /**
     * Whether {@code candidate} is within the minimum separation distance of any
     * existing platform position.
     */
    public static boolean violatesProximity(List<Vector> existing, Vector candidate) {
        for (Vector position : existing) {
            if (position.distanceSquared(candidate) < MIN_PLATFORM_DISTANCE_SQ) {
                return true;
            }
        }
        return false;
    }

    /**
     * The semi-transparent glass material a platform shows at a given age,
     * progressing green -> lime -> yellow -> orange -> red.
     */
    public static Material decayBlock(long ageMs) {
        if (ageMs >= DECAY_RED_MS) {
            return Material.RED_STAINED_GLASS;
        }
        if (ageMs >= DECAY_ORANGE_MS) {
            return Material.ORANGE_STAINED_GLASS;
        }
        if (ageMs >= DECAY_YELLOW_MS) {
            return Material.YELLOW_STAINED_GLASS;
        }
        if (ageMs >= DECAY_LIME_MS) {
            return Material.LIME_STAINED_GLASS;
        }
        return Material.GREEN_STAINED_GLASS;
    }

    /**
     * Continuous colour for a platform at a given age, interpolated through hue
     * from green (fresh) to red (expiring).
     */
    public static Color platformColor(long ageMs) {
        float t = (float) Math.min(1.0, (double) ageMs / PLATFORM_LIFETIME_MS);
        return Color.fromRGB(hsvToRgb(120f * (1f - t)));
    }

    /**
     * HSV -> RGB for full saturation/value hues in the 0..120 degree range (red ..
     * green).
     */
    private static int hsvToRgb(float hue) {
        float h = hue % 360f;
        if (h < 0f) {
            h += 360f;
        }
        int red;
        int green;
        if (h < 60f) {
            red = 255;
            green = Math.round(255f * h / 60f);
        } else {
            red = Math.round(255f * (1f - (h - 60f) / 60f));
            green = 255;
        }
        return (red << 16) | (green << 8);
    }

    /** Whether a squared distance falls in the valid dash range. */
    public static boolean insideDashRange(double distanceSquared) {
        return distanceSquared >= DASH_RANGE_MIN * DASH_RANGE_MIN && distanceSquared <= DASH_RANGE_MAX * DASH_RANGE_MAX;
    }

    /**
     * Whether the normalized look vector is closely aligned with the vector to a
     * platform (crosshair lock).
     */
    public static boolean isAimingAt(Vector lookDirection, Vector toPlatform, double dotThreshold) {
        return lookDirection.clone().normalize().dot(toPlatform.clone().normalize()) >= dotThreshold;
    }

    /** Linear interpolation progress (0..1) for a dash step. */
    public static double dashStep(int step, int totalTicks) {
        return (step + 1.0) / totalTicks;
    }

    /**
     * Raw physical damage dealt when dashing through an enemy:
     * {@code 0.6 * ATTACK_DAMAGE + 0.3 * LETHALITY}.
     */
    public static double calculateDashThroughDamage(double attackDamage, double lethality) {
        return attackDamage * DASH_DAMAGE_AD_RATIO + lethality * DASH_DAMAGE_LETHALITY_RATIO;
    }

    /**
     * Squared distance from {@code point} to the closest point on the segment
     * {@code segStart -> segEnd}.
     */
    public static double distanceToSegmentSquared(Location point, Location segStart, Location segEnd) {
        Vector seg = segEnd.toVector().subtract(segStart.toVector());
        Vector toPoint = point.toVector().subtract(segStart.toVector());
        double segLenSq = seg.lengthSquared();
        if (segLenSq == 0) {
            return toPoint.lengthSquared();
        }
        double t = toPoint.dot(seg) / segLenSq;
        t = Math.max(0, Math.min(1, t));
        Vector projection = segStart.toVector().add(seg.clone().multiply(t));
        return point.toVector().distanceSquared(projection);
    }

    /**
     * Whether an entity position is within {@code radius} of the dash segment.
     */
    public static boolean isDashHit(Location point, Location segStart, Location segEnd, double radius) {
        return distanceToSegmentSquared(point, segStart, segEnd) <= radius * radius;
    }

    private static AxisAngle4f identityAxis() {
        return new AxisAngle4f(0f, 0f, 0f, 1f);
    }

    public boolean isHolder(Player p) {
        return p.getUniqueId().equals(ctx.getHolder().getUuid());
    }

    // ============================================================== STATE TYPES

    /** One player's entire staff runtime state. */
    private static final class PlayerState {
        final List<Platform> platforms = new ArrayList<>();
        BlockDisplay indicator;
        Location target;
        Platform lockedOn;
        long lockedOnAt;
        Platform lockedPlatform;
        Location lockedAt;
        boolean dashing;
        int dashTick;
        Location dashStart;
        Location dashEnd;
        Platform dashTarget;
        long plungeExpiresAt;
        final Set<UUID> dashHitEntities = ConcurrentHashMap.newKeySet();

        boolean isLocked() {
            return lockedPlatform != null;
        }

        boolean isDashing() {
            return dashing;
        }

        void showIndicator(Location at, boolean valid) {
            Location center = at.clone();
            Color glowColor = valid ? Color.fromRGB(0x00, 0xFF, 0x66) : Color.fromRGB(0xFF, 0x33, 0x33);
            Material displayBlock = valid ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS;

            if (indicator == null) {
                indicator = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(center, BlockDisplay.class,
                        d -> {
                            d.setBlock(displayBlock.createBlockData());
                            d.setBrightness(new Display.Brightness(15, 15));
                            d.setGlowing(true);
                            d.setGlowColorOverride(glowColor);
                            d.setTeleportDuration(1);
                            d.setTransformation(new Transformation(new Vector3f(-0.35f, -0.05f, -0.35f), identityAxis(),
                                    new Vector3f(0.7f, 0.1f, 0.7f), identityAxis()));
                        });
            } else {
                indicator.setBlock(displayBlock.createBlockData());
                indicator.setGlowColorOverride(glowColor);
                indicator.teleport(center);
            }

            // Render a lightweight cloud swirl preview around the target location
            World world = at.getWorld();
            if (world != null) {
                Particle.DustOptions dust = new Particle.DustOptions(glowColor, 0.9f);
                world.spawnParticle(Particle.DUST, center, 3, 0.25, 0.05, 0.25, 0, dust);
                if (valid) {
                    world.spawnParticle(Particle.END_ROD, center, 1, 0.1, 0.02, 0.1, 0.005);
                }
            }
        }

        void hideIndicator() {
            if (indicator != null) {
                indicator.remove();
                indicator = null;
            }
        }

        void clearLockOn() {
            if (lockedOn != null) {
                lockedOn.display.setGlowing(false);
                lockedOn.display.setGlowColorOverride(null);
            }
            lockedOn = null;
        }

        void releaseLock(Player player) {
            if (isLocked()) {
                lockedPlatform = null;
                lockedAt = null;
                player.setGravity(true);
            }
            if (isDashing()) {
                dashing = false;
                dashHitEntities.clear();
                player.setGravity(true);
            }
            clearLockOn();
        }

        void disposeAll() {
            hideIndicator();
            clearLockOn();
            for (Platform platform : platforms) {
                if (!platform.isInvalid()) {
                    platform.display.remove();
                }
            }
            platforms.clear();
            lockedPlatform = null;
            lockedAt = null;
            dashing = false;
            dashHitEntities.clear();
            dashStart = null;
            dashEnd = null;
            dashTarget = null;
        }
    }

    /** An active shadow platform: a BlockDisplay plus its anchor bookkeeping. */
    private static final class Platform {
        final BlockDisplay display;
        final Location location;
        final long spawnedAt;
        Material currentBlock;

        Platform(BlockDisplay display, Location location, long spawnedAt) {
            this.display = display;
            this.location = location;
            this.spawnedAt = spawnedAt;
            this.currentBlock = Material.GREEN_STAINED_GLASS;
        }

        boolean isInvalid() {
            return !display.isValid();
        }
    }
}
