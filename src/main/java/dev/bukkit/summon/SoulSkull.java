package dev.bukkit.summon;

import java.util.UUID;
import java.util.WeakHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitDisplayEntityRegistry;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.utils.AdventureText;
import dev.core.utils.LegacyColors;

/**
 * The dropped soul pickup on the ground: a small, glowing purple skull that
 * hovers at the kill site for a short while. A Support player carrying a Soul
 * Tome collects it by running into it (the tome's Soul Collector passive); the
 * captured {@link SoulFragment} is stored in the tome before the skull
 * despawns.
 *
 * <p>
 * The soul is rendered with an {@link ItemDisplay} (skull) plus a
 * {@link TextDisplay} countdown. Display entities have no hitbox, so arrows,
 * melee and ability ray traces pass through them; only players carrying a Soul
 * Tome can see the displays and their particles.
 * </p>
 */
public final class SoulSkull {

    /** Skulls despawn after this long. */
    public static final int DESPAWN_SECONDS = 20;

    /**
     * Radius (blocks) around the skull a tome holder must get within to collect it.
     */
    private static final double COLLECT_RADIUS = 1.5;

    private static final NamespacedKey SOUL_KEY = new NamespacedKey("project_d", "soulskull");
    private static final DustOptions PURPLE = new DustOptions(Color.fromRGB(170, 60, 220), 1.2f);
    private static final int PARTICLE_INTERVAL_TICKS = 5;
    private static final float SKULL_SCALE = 0.6f;
    private static final float TEXT_SCALE = 0.8f;

    private static final String SKULL_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2U5NTZiMDQ3ZmJhMTYwZWU3MmNkMDE0MmU1MWM5YTU5MTBjODZmYTZlZDkyYWUwZTI2OWQ5YTI5YjY2ZjgwMiJ9fX0=";

    private SoulSkull() {
    }

    /**
     * Spawns a purple soul skull at the given location. The skull hovers slightly
     * above the ground, shimmers with purple particles and removes itself after
     * {@link #DESPAWN_SECONDS}. It is only visible to players carrying a Soul Tome;
     * those players collect the soul just by getting close.
     */
    public static SoulDisplay spawn(Location location, SoulFragment fragment) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location at = location.clone().add(0, 0.55, 0);
        String encoded = SoulTome.encode(fragment);

        ItemDisplay item = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(at, ItemDisplay.class, d -> {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            skull.editMeta(SkullMeta.class, skullMeta -> {
                UUID uuid = UUID.randomUUID();
                PlayerProfile playerProfile = Bukkit.createProfile(uuid, uuid.toString().substring(0, 16));
                playerProfile.setProperty(new ProfileProperty("textures", SKULL_TEXTURE));
                skullMeta.setPlayerProfile(playerProfile);
            });
            d.setItemStack(skull);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(32);
            d.setShadowStrength(0f);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(170, 60, 220));
            d.getPersistentDataContainer().set(SOUL_KEY, PersistentDataType.STRING, encoded);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(SKULL_SCALE),
                    new Quaternionf()));
        });

        Location textLoc = at.clone().add(0, 0.75, 0);
        TextDisplay text = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(textLoc, TextDisplay.class,
                d -> {
                    d.text(AdventureText.legacy(formatLabel(DESPAWN_SECONDS * 20L)));
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setSeeThrough(true);
                    d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                    d.setGravity(false);
                    d.setInvulnerable(true);
                    d.setPersistent(false);
                    d.setShadowed(true);
                    d.setViewRange(32);
                    d.getPersistentDataContainer().set(SOUL_KEY, PersistentDataType.STRING, encoded);
                    Transformation transformation = d.getTransformation();
                    transformation.getScale().set(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
                    d.setTransformation(transformation);
                });

        SoulDisplay soul = new SoulDisplay(item, text, fragment);

        BukkitRunnable aura = new BukkitRunnable() {
            /**
             * Last visibility state sent per player. Keyed by the Player instance (weakly
             * held) so rejoining players are treated as unseen again and entries clean
             * themselves up on logout.
             */
            private final WeakHashMap<Player, Boolean> visibilityState = new WeakHashMap<>();
            private long remainingTicks = DESPAWN_SECONDS * 20L;

            @Override
            public void run() {
                if (soul.isRemoved()) {
                    soul.remove();
                    cancel();
                    return;
                }
                if (remainingTicks <= 0) {
                    soul.remove();
                    cancel();
                    return;
                }

                updateTimer();
                if (remainingTicks % PARTICLE_INTERVAL_TICKS == 0) {
                    updateVisibilityAndParticles();
                    if (collectNearby()) {
                        cancel();
                        return;
                    }
                }
                remainingTicks--;
            }

            private void updateTimer() {
                text.text(AdventureText.legacy(formatLabel(remainingTicks)));
            }

            /**
             * Souls are only visible to players carrying a Soul Tome: everyone else gets
             * both displays hidden via {@code Player#hideEntity}. Packets are only sent
             * when a player's visibility actually changes, and dust particles are sent
             * per-player so non-holders never see them.
             */
            private void updateVisibilityAndParticles() {
                DMain plugin = DMain.getInstance();
                if (plugin == null) {
                    return;
                }
                Location particleLoc = item.getLocation().add(0, 0.35, 0);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    boolean shouldSee = SoulTome.findTome(player) != null;
                    Boolean known = visibilityState.get(player);
                    if (known == null || known != shouldSee) {
                        try {
                            if (shouldSee) {
                                player.showEntity(plugin, item);
                                player.showEntity(plugin, text);
                            } else {
                                player.hideEntity(plugin, item);
                                player.hideEntity(plugin, text);
                            }
                            visibilityState.put(player, shouldSee);
                        } catch (Exception ignored) {
                        }
                    }
                    if (shouldSee && player.getWorld().equals(world)) {
                        player.spawnParticle(Particle.DUST, particleLoc, 2, 0.3, 0.3, 0.3, PURPLE);
                    }
                }
            }

            /**
             * Soul Collector passive: any tome-holding Support within
             * {@link #COLLECT_RADIUS} collects the soul on contact. First eligible player
             * wins; the skull is removed by a successful capture.
             */
            private boolean collectNearby() {
                for (Entity entity : world.getNearbyEntities(item.getLocation(), COLLECT_RADIUS, COLLECT_RADIUS,
                        COLLECT_RADIUS)) {
                    if (!(entity instanceof Player player)) {
                        continue;
                    }
                    if (tryCapture(player, soul)) {
                        return true;
                    }
                }
                return false;
            }
        };
        aura.runTaskTimer(DMain.getInstance(), 0L, 1L);
        soul.setTask(aura);
        return soul;
    }

    /**
     * Attempts to store the soul in the player's Soul Tome. Runs the full capture
     * validation (Support class, tome present, tier gate, capacity).
     */
    private static boolean tryCapture(Player player, SoulDisplay soul) {
        SoulFragment fragment = soul.fragment();
        if (fragment == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (DMain.getInstance().getProgressionService().getActiveClass(playerId) != RPGClassType.SUPPORT) {
            return false; // not a Support: no claim, no spam
        }

        ItemStack tome = SoulTome.findTome(player);
        if (tome == null) {
            return false; // no tome: nothing to capture into
        }

        int level = DMain.getInstance().getProgressionService().getProgression(playerId, RPGClassType.SUPPORT)
                .getLevel();
        if (!SummonStats.canCapture(level, fragment.tier())) {
            player.sendMessage(LegacyColors.RED + "This soul is too powerful for your Support level to capture.");
            return false;
        }

        int capacity = SummonStats.capacityForLevel(level);
        if (SoulTome.countSouls(tome) >= capacity) {
            player.sendMessage(LegacyColors.RED + "Your Soul Tome is full (max " + capacity + ").");
            return false;
        }

        SoulTome.addSoul(tome, fragment, capacity);
        soul.remove();
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 1.6f);
        player.sendMessage(LegacyColors.GREEN + "Captured " + fragment.mobType().name().toLowerCase().replace('_', ' ')
                + " soul (" + (fragment.tier().name().toLowerCase()) + "). " + LegacyColors.GRAY
                + SoulTome.countSouls(tome) + "/" + capacity + " souls held.");
        return true;
    }

    public static boolean isSoulSkull(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(SOUL_KEY, PersistentDataType.STRING);
    }

    public static SoulFragment getSoul(Entity entity) {
        if (!isSoulSkull(entity)) {
            return null;
        }
        return SoulTome.decode(entity.getPersistentDataContainer().get(SOUL_KEY, PersistentDataType.STRING));
    }

    /**
     * Formats the remaining time as seconds plus one fractional digit (tenths),
     * e.g. {@code 19:9}.
     */
    static String formatRemaining(long remainingTicks) {
        long millis = Math.max(0L, remainingTicks) * 50L;
        long seconds = millis / 1000L;
        long tenths = (millis % 1000L) / 100L;
        return String.format("%d:%d", seconds, tenths);
    }

    private static String formatLabel(long remainingTicks) {
        return LegacyColors.LIGHT_PURPLE + "Soul\n" + LegacyColors.WHITE + formatRemaining(remainingTicks);
    }

    /**
     * The pair of display entities that make up one dropped soul. Removing the
     * wrapper always removes both displays, so capture/despawn/external removal
     * cannot leave an orphaned countdown behind.
     */
    public static final class SoulDisplay {

        private final ItemDisplay item;
        private final TextDisplay text;
        private final SoulFragment fragment;
        private BukkitRunnable task;
        private boolean removed;

        private SoulDisplay(ItemDisplay item, TextDisplay text, SoulFragment fragment) {
            this.item = item;
            this.text = text;
            this.fragment = fragment;
        }

        public ItemDisplay item() {
            return item;
        }

        public TextDisplay text() {
            return text;
        }

        public SoulFragment fragment() {
            return fragment;
        }

        public boolean isRemoved() {
            return removed || !item.isValid() || !text.isValid();
        }

        private void setTask(BukkitRunnable task) {
            this.task = task;
        }

        public void remove() {
            if (removed) {
                return;
            }
            removed = true;
            if (task != null) {
                task.cancel();
                task = null;
            }
            BukkitDisplayEntityRegistry registry = BukkitDisplayEntityRegistry.getInstance();
            UUID itemId = item.getUniqueId();
            UUID textId = text.getUniqueId();
            if (item.isValid()) {
                item.remove();
            }
            if (text.isValid()) {
                text.remove();
            }
            registry.untrack(itemId);
            registry.untrack(textId);
        }
    }
}
