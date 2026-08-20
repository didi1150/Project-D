package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import dev.bukkit.item.BukkitItemStackAdapter;

/**
 * Owns every piece of the Shadow Weaver's Staff runtime that runs while the
 * assassin merely holds the item: the raycast placement preview, the platform
 * lifecycle (spawn, decay, despawn), the dash target lock, the interpolated
 * dash animation and the sticky "float on the platform" lock. Click actions are
 * delivered by the ability pipeline (see {@link BukkitShadowWeaverPlaceEffect}
 * and {@link BukkitShadowWeaverDashEffect}) which delegate here.
 *
 * <p>
 * State is mapped by player UUID and kept entirely server-side; the only
 * entities spawned are {@link BlockDisplay} platforms and a single preview
 * marker per player. Real Minecraft blocks are never placed, so entities pass
 * straight through the visual platforms. Platforms persist for their full
 * lifetime no matter how the player leaves them: only the 6-second duration (or
 * the 3-platform cap) removes them, so the assassin can hop freely between old
 * and new platforms.
 */
public class ShadowWeaverManager implements Listener {

	/** Item id the staff behaviors bind to (see items.yml). */
	public static final String ITEM_ID = "SHADOW_WEAVER_STAFF";

	// ---- Raycast / placement ------------------------------------------------
	/** Maximum raycast range (blocks) for the placement preview. */
	public static final int RAYCAST_MAX_BLOCKS = 5;
	/** Step size of the placement raycast. */
	private static final double RAYCAST_STEP = 0.5;
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

	/**
	 * Horizontal displacement (blocks) from the snap point that counts as a manual
	 * WASD drop.
	 */
	private static final double DROP_MOVE_THRESHOLD = 0.2;

	private final Map<UUID, PlayerState> states = new HashMap<>();
	private boolean running;
	private BukkitTask tickTask;

	private static ShadowWeaverManager instance;

	private ShadowWeaverManager() {
	}

	/**
	 * Singleton access; callers must not use the manager before
	 * {@link #start(Plugin)} is invoked.
	 */
	public static ShadowWeaverManager getInstance() {
		if (instance == null) {
			instance = new ShadowWeaverManager();
		}
		return instance;
	}

	/** Starts the per-tick runnable and registers listeners. Idempotent. */
	public void start(Plugin plugin) {
		if (running) {
			return;
		}
		Bukkit.getPluginManager().registerEvents(this, plugin);
		tickTask = new BukkitRunnable() {
			@Override
			public void run() {
				tickAll();
			}
		}.runTaskTimer(plugin, 0L, 1L);
		running = true;
	}

	/** Stops the runnable and tears down every player's state. */
	public void stop() {
		if (!running) {
			return;
		}
		running = false;
		if (tickTask != null) {
			tickTask.cancel();
			tickTask = null;
		}
		cleanupAll();
	}

	// ============================================================== PLAYER CLICKS

	/** Right-click placement action, invoked by the place effect. */
	public void handlePlace(Player player) {
		PlayerState state = state(player);
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
		PlayerState state = state(player);
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

	private void tickAll() {
		long now = System.currentTimeMillis();
		for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
			PlayerState state = state(player);
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
			// even after the staff is sheathed.
			tickPlatforms(player, state, now);
		}
		// Garbage collect states for players who are gone.
		states.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null);
	}

	private void tickState(Player player, PlayerState state, long now) {
		if (state.isDashing()) {
			advanceDash(player, state);
		} else if (state.isLocked() && shouldDrop(player, state)) {
			releasePlatform(player, state);
		}

		updateIndicator(player, state);
		updateTargetLock(player, state, now);
		// Platform decay is advanced for every tracked player in tickAll.
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

	/**
	 * Steps along the eye ray until a non-passable block is met, returning the last
	 * passable block location (or the ray's endpoint when the whole ray is clear).
	 * Null when the ray immediately starts inside solid geometry.
	 */
	private static Location raycastTarget(Location eye, Vector dir, int maxBlocks) {
		Location cursor = null;
		for (double t = 0; t <= maxBlocks + 1e-9; t += RAYCAST_STEP) {
			Location probe = eye.clone().add(dir.clone().multiply(t));
			Block block = probe.getBlock();
			if (block.getType().isSolid() || !block.isPassable()) {
				break;
			}
			cursor = probe;
		}
		if (cursor == null) {
			return null;
		}
		return cursor.getBlock().getLocation();
	}

	/**
	 * A placement is valid when its target block is air, in range and further than
	 * the minimum separation from every active platform.
	 */
	private static boolean isValidPlacement(Player player, PlayerState state, Location target) {
		if (!target.getBlock().isPassable()) {
			return false;
		}
		if (player.getEyeLocation().distanceSquared(target) > RAYCAST_MAX_BLOCKS * RAYCAST_MAX_BLOCKS) {
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
		World world = target.getWorld();
		Location center = target.clone().add(0.5, 0.5, 0.5);

		// Scaled, flattened core display that glows brilliantly inside the particle
		// cloud
		BlockDisplay display = world.spawn(center, BlockDisplay.class, d -> {
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
		player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 0.4f, 0.9f);
	}

	private void advanceDash(Player player, PlayerState state) {
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
		player.getWorld().spawnParticle(Particle.SMOKE, step, 3, 0.1, 0.1, 0.1, 0.02);
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

	// ============================================================== PLUNGE STRIKE

	/**
	 * Consumes the assassin's armed Plunge Strike: the first melee hit within 3s of
	 * leaving a platform deals {@value #PLUNGE_MULTIPLIER}x damage. Returns the
	 * multiplier (1.0 when not armed) and clears the window once used.
	 */
	public double consumePlungeMultiplier(UUID attackerUuid) {
		PlayerState state = states.get(attackerUuid);
		if (state == null) {
			return 1.0;
		}
		if (System.currentTimeMillis() > state.plungeExpiresAt) {
			return 1.0;
		}
		state.plungeExpiresAt = 0;
		return PLUNGE_MULTIPLIER;
	}

	// ============================================================== ENTITY
	// LIFECYCLE

	private PlayerState state(Player player) {
		return states.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerState());
	}

	private PlayerState state(UUID uuid) {
		return states.computeIfAbsent(uuid, key -> new PlayerState());
	}

	private boolean holdsStaff(Player player) {
		return ITEM_ID.equals(BukkitItemStackAdapter.getRpgItemId(player.getInventory().getItemInMainHand()));
	}

	/** Removes every display and restores gravity for all tracked players. */
	private void cleanupAll() {
		for (Map.Entry<UUID, PlayerState> entry : new HashMap<>(states).entrySet()) {
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null) {
				entry.getValue().releaseLock(player);
			}
			entry.getValue().disposeAll();
		}
		states.clear();
	}

	// ============================================================== EVENTS

	@EventHandler(priority = EventPriority.MONITOR)
	public void onMove(PlayerMoveEvent event) {
		PlayerState state = states.get(event.getPlayer().getUniqueId());
		if (state != null && state.isLocked() && shouldDrop(event.getPlayer(), state)) {
			releasePlatform(event.getPlayer(), state);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onSneak(PlayerToggleSneakEvent event) {
		if (!event.isSneaking()) {
			return;
		}
		PlayerState state = states.get(event.getPlayer().getUniqueId());
		if (state != null && state.isLocked()) {
			releasePlatform(event.getPlayer(), state);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		cleanupPlayer(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		cleanupPlayer(event.getEntity());
	}

	private void cleanupPlayer(Player player) {
		PlayerState state = states.remove(player.getUniqueId());
		if (state != null) {
			state.releaseLock(player);
			state.disposeAll();
		}
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

	private static AxisAngle4f identityAxis() {
		return new AxisAngle4f(0f, 0f, 0f, 1f);
	}

	// ============================================================== STATE TYPES

	/** One player's entire staff runtime state. */
	static final class PlayerState {
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

		boolean isLocked() {
			return lockedPlatform != null;
		}

		boolean isDashing() {
			return dashing;
		}

		void showIndicator(Location at, boolean valid) {
			Location center = at.clone().add(0.5, 0.5, 0.5);
			Color glowColor = valid ? Color.fromRGB(0x00, 0xFF, 0x66) : Color.fromRGB(0xFF, 0x33, 0x33);
			Material displayBlock = valid ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS;

			if (indicator == null) {
				World world = at.getWorld();
				indicator = world.spawn(center, BlockDisplay.class, d -> {
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
			dashStart = null;
			dashEnd = null;
			dashTarget = null;
		}
	}

	/** An active shadow platform: a BlockDisplay plus its anchor bookkeeping. */
	static final class Platform {
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