package dev.bukkit.game.boss.ritual;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import dev.bukkit.entity.BukkitDisplayEntityRegistry;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.BukkitText;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.utils.AdventureText;
import net.kyori.adventure.title.Title;

public final class SealedWipeRitual {

    public record Settings(int durationTicks, int killTick, int pullTicks, int drawTicks, int riseStartTick,
            int riseTicks, int gripStartTick, int gripTicks, double radius, double hoverHeight, double handDepth,
            double handHeight, boolean killPlayers, Material handMaterial) {
    }

    private static final Particle.DustOptions STAR_DUST = new Particle.DustOptions(Color.fromRGB(110, 0, 0), 1.2f);
    private static final Particle.DustOptions RING_DUST = new Particle.DustOptions(Color.fromRGB(60, 0, 0), 0.9f);

    private final World world;
    private final Location center;
    private final Settings settings;
    private final Random random = new Random();

    private final List<UUID> handDisplays = new ArrayList<>();
    private final List<BlockDisplay> fingers = new ArrayList<>();
    private final List<BlockDisplay> fingertips = new ArrayList<>();
    private final List<UUID> players = new ArrayList<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();

    private BlockDisplay palm;
    private boolean killed;
    private boolean riseSoundPlayed;
    private boolean gripTitleShown;

    public SealedWipeRitual(World world, Location center, Settings settings) {
        this.world = world;
        this.center = center.clone();
        this.settings = settings;
    }

    public void start() {
        spawnHand();
        if (settings.killPlayers()) {
            collectPlayers();
            for (UUID id : players) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                player.setGravity(false);
                player.setInvulnerable(true);
                player.setFallDistance(0);
                player.setVelocity(new Vector());
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, settings.durationTicks() + 40, 0,
                        false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, settings.durationTicks() + 40, 2,
                        false, false));
                showTitle(player, "§4THE SEALED HAND", "§7The seal has broken.");
            }
        }
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 3.0f, 0.5f);
    }

    public void tick(long tick) {
        if (tick % 2 == 0) {
            drawPentagram(tick);
        }
        updateHand(tick);
        if (settings.killPlayers() && !killed) {
            updatePlayers(tick);
        }
        playAmbience(tick);
        if (settings.killPlayers() && !killed && tick >= settings.killTick()) {
            killAll();
        }
    }

    public void stop() {
        BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();
        for (UUID id : handDisplays) {
            registry.removeDisplayEntity(id);
        }
        handDisplays.clear();
        palm = null;
        fingers.clear();
        fingertips.clear();

        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            player.setGravity(true);
            player.setInvulnerable(false);
            player.setFallDistance(0);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            if (!EntityManager.getInstance().isGhost(id) && player.getWorld().equals(world)) {
                Location start = startLocations.get(id);
                if (start != null) {
                    player.teleport(start);
                }
            }
        }
        players.clear();
        startLocations.clear();
    }

    private void collectPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            UUID id = player.getUniqueId();
            if (EntityManager.getInstance().isGhost(id)) {
                continue;
            }
            Optional<RPGEntity> entity = EntityManager.getInstance().getEntity(id);
            if (entity.isEmpty() || !entity.get().isAlive()) {
                continue;
            }
            players.add(id);
            startLocations.put(id, player.getLocation().clone());
        }
        players.sort(Comparator.comparing(id -> {
            Player player = Bukkit.getPlayer(id);
            return player == null ? "" : player.getName();
        }));
    }

    private void updatePlayers(long tick) {
        if (players.isEmpty()) {
            return;
        }
        double progress = settings.pullTicks() <= 0 ? 1 : Math.min(1, tick / (double) settings.pullTicks());
        double eased = smoothstep(progress);
        double handOffset = handYOffset(tick);
        int index = 0;
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.getWorld().equals(world) || EntityManager.getInstance().isGhost(id)) {
                index++;
                continue;
            }
            Location start = startLocations.get(id);
            if (start == null) {
                index++;
                continue;
            }

            double angle = Math.PI * 2 * index / Math.max(1, players.size()) - Math.PI / 2;
            double slotX = center.getX() + Math.cos(angle) * 1.4;
            double slotZ = center.getZ() + Math.sin(angle) * 1.4;
            double slotY = center.getY() + settings.hoverHeight() + Math.sin((tick + index * 7) / 9.0) * 0.12;
            if (tick >= settings.gripStartTick()) {
                slotX += (random.nextDouble() - 0.5) * 0.14;
                slotZ += (random.nextDouble() - 0.5) * 0.14;
            }

            Location target = new Location(world, lerp(start.getX(), slotX, eased), lerp(start.getY(), slotY, eased),
                    lerp(start.getZ(), slotZ, eased));
            Vector look = center.clone().add(0, handOffset, 0).toVector().subtract(target.toVector());
            if (look.lengthSquared() > 0.0001) {
                target.setDirection(look);
            }
            player.setFallDistance(0);
            player.setVelocity(new Vector());
            player.teleport(target);
            index++;
        }
    }

    private void updateHand(long tick) {
        if (palm == null) {
            return;
        }
        double rise = settings.riseTicks() <= 0 ? 1
                : clamp((tick - settings.riseStartTick()) / (double) settings.riseTicks());
        double handOffset = handYOffset(tick);
        double grip = settings.gripTicks() <= 0 ? 0
                : clamp((tick - settings.gripStartTick()) / (double) settings.gripTicks());

        setPart(palm, 0, (float) handOffset, 0, new Vector3f(3.2f, 0.7f, 2.6f), new Quaternionf());
        for (int i = 0; i < 5; i++) {
            float x = (i - 2) * 0.62f;
            BlockDisplay proximal = fingers.get(i * 2);
            BlockDisplay distal = fingers.get(i * 2 + 1);

            Quaternionf proximalRotation = new Quaternionf().rotationX((float) (-0.2 * grip));
            setPart(proximal, x, (float) handOffset + 0.9f, 1.15f, new Vector3f(0.36f, 1.7f, 0.36f),
                    proximalRotation);

            float distalX = x * (1 - 0.2f * (float) grip);
            float distalY = (float) handOffset + 2.2f - 0.5f * (float) grip;
            float distalZ = 1.15f + 0.55f * (float) grip;
            Quaternionf distalRotation = new Quaternionf().rotationX((float) (-0.9 * grip));
            setPart(distal, distalX, distalY, distalZ, new Vector3f(0.3f, 1.35f, 0.3f), distalRotation);

            setPart(fingertips.get(i), distalX, distalY + 0.75f - 0.15f * (float) grip,
                    distalZ + 0.3f * (float) grip, new Vector3f(0.24f, 0.24f, 0.24f), new Quaternionf());
        }

        if (rise < 1 && tick % 2 == 0) {
            world.spawnParticle(Particle.BLOCK_CRUMBLE, center.clone().add(0, 0.2, 0), 16, 2.2, 0.3, 2.2, 0.04,
                    settings.handMaterial().createBlockData());
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 0.5, 0), 8, 2.0, 0.5, 2.0,
                    0.02);
        }
        if (grip > 0 && tick % 2 == 0) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, handOffset, 0), 8, 2.0, 1.0, 2.0,
                    0.02);
        }
    }

    private void drawPentagram(long tick) {
        double progress = settings.drawTicks() <= 0 ? 1 : Math.min(1, tick / (double) settings.drawTicks());
        double[] xs = new double[5];
        double[] zs = new double[5];
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(-90 + i * 72);
            xs[i] = center.getX() + Math.cos(angle) * settings.radius();
            zs[i] = center.getZ() + Math.sin(angle) * settings.radius();
        }

        int[] order = { 0, 2, 4, 1, 3, 0 };
        int pointsPerLine = 28;
        int drawn = (int) (pointsPerLine * progress);
        for (int line = 0; line < 5; line++) {
            for (int point = 0; point < drawn; point++) {
                double t = point / (double) (pointsPerLine - 1);
                double x = lerp(xs[order[line]], xs[order[line + 1]], t);
                double z = lerp(zs[order[line]], zs[order[line + 1]], t);
                Location loc = new Location(world, x, center.getY() + 0.08, z);
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, STAR_DUST);
                if (point % 4 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0, 0, 0, 0.002);
                }
            }
        }

        if (progress >= 1) {
            double rotation = tick * 0.04;
            for (int i = 0; i < 48; i++) {
                double angle = rotation + i / 48.0 * Math.PI * 2;
                Location loc = new Location(world, center.getX() + Math.cos(angle) * (settings.radius() + 0.4),
                        center.getY() + 0.12, center.getZ() + Math.sin(angle) * (settings.radius() + 0.4));
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, RING_DUST);
            }
        }
    }

    private void playAmbience(long tick) {
        if (!riseSoundPlayed && tick >= settings.riseStartTick()) {
            riseSoundPlayed = true;
            world.playSound(center, Sound.ENTITY_WARDEN_EMERGE, 3.0f, 0.45f);
        }
        if (!gripTitleShown && tick >= settings.gripStartTick()) {
            gripTitleShown = true;
            world.playSound(center, Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.4f);
            for (UUID id : players) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    showTitle(player, "§4NO ESCAPE", "§7The hand closes.");
                }
            }
        }
        if (!killed && tick < settings.killTick() && tick % 25 == 0) {
            world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.7f);
        }
    }

    private void killAll() {
        killed = true;
        world.playSound(center, Sound.ENTITY_WITHER_DEATH, 4.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.4f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, settings.hoverHeight(), 0), 4, 2, 1, 2,
                0);
        BukkitText.broadcast("§4The Sealed Hand drags the party into the dark.");

        Set<UUID> targets = new LinkedHashSet<>(players);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && !EntityManager.getInstance().isGhost(player.getUniqueId())) {
                targets.add(player.getUniqueId());
            }
        }

        for (UUID id : targets) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.getWorld().equals(world) || EntityManager.getInstance().isGhost(id)) {
                continue;
            }
            Location loc = player.getLocation();
            world.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 40, 0.6, 1.2, 0.6, 0.06);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20, 0.5, 0.9, 0.5, 0.02);
            EntityManager.getInstance().getEntity(id).filter(entity -> entity instanceof BukkitPlayerEntity)
                    .ifPresent(RPGEntity::onDeath);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    private void spawnHand() {
        palm = spawnPart(settings.handMaterial(), 0);
        for (int i = 0; i < 5; i++) {
            fingers.add(spawnPart(settings.handMaterial(), 0));
            fingers.add(spawnPart(settings.handMaterial(), 0));
            fingertips.add(spawnPart(Material.REDSTONE_BLOCK, 15));
        }
        updateHand(0);
    }

    private BlockDisplay spawnPart(Material material, int brightness) {
        BlockDisplay display = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(center.clone(),
                BlockDisplay.class, d -> {
                    d.setBlock(material.createBlockData());
                    d.setPersistent(false);
                    d.setInvulnerable(true);
                    d.setGravity(false);
                    d.setViewRange(64f);
                    d.setInterpolationDuration(0);
                    d.setBrightness(new Display.Brightness(brightness, brightness));
                    d.setTransformation(new Transformation(new Vector3f(0, (float) -settings.handDepth() - 4f, 0),
                            new Quaternionf(), new Vector3f(), new Quaternionf()));
                });
        handDisplays.add(display.getUniqueId());
        return display;
    }

    private double handYOffset(long tick) {
        double rise = settings.riseTicks() <= 0 ? 1
                : clamp((tick - settings.riseStartTick()) / (double) settings.riseTicks());
        return -settings.handDepth() + (settings.handDepth() + settings.handHeight()) * smoothstep(rise);
    }

    private static void setPart(BlockDisplay display, float x, float y, float z, Vector3f scale,
            Quaternionf rotation) {
        display.setTransformation(new Transformation(new Vector3f(x, y, z), rotation, scale, new Quaternionf()));
    }

    private static void showTitle(Player player, String title, String subtitle) {
        player.showTitle(Title.title(AdventureText.legacy(title), AdventureText.legacy(subtitle),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))));
    }

    private static double smoothstep(double progress) {
        double t = clamp(progress);
        return t * t * (3 - 2 * t);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
