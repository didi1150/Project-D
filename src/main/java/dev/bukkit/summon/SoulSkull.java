package dev.bukkit.summon;

import java.util.UUID;
import java.util.WeakHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import dev.bukkit.DMain;
import dev.core.entity.rpgclass.RPGClassType;

/**
 * The dropped soul pickup on the ground: a small, glowing purple skull that
 * hovers at the kill site for a short while. A Support player carrying a Soul
 * Tome collects it by running into it (the tome's Soul Collector passive); the
 * captured {@link SoulFragment} is stored in the tome before the skull
 * despawns.
 */
public final class SoulSkull {

    /** Skulls despawn after this long. */
    public static final int DESPAWN_SECONDS = 20;

    /**
     * Radius (blocks) around the skull a tome holder must get within to collect it.
     */
    private static final double COLLECT_RADIUS = 2.5;

    private static final NamespacedKey SOUL_KEY = new NamespacedKey("project_d", "soulskull");
    private static final DustOptions PURPLE = new DustOptions(Color.fromRGB(170, 60, 220), 1.2f);

    private SoulSkull() {
    }

    /**
     * Spawns a purple soul skull at the given location. The skull hovers slightly
     * above the ground, shimmers with purple particles and removes itself after
     * {@link #DESPAWN_SECONDS}. It is only visible to players carrying a Soul Tome;
     * those players collect the soul just by getting close.
     */
    public static ArmorStand spawn(Location location, SoulFragment fragment) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location at = location.clone().add(0, 0.55, 0);
        ArmorStand stand = world.spawn(at, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setSmall(true);
            s.setGravity(false);
            s.setCanPickupItems(false);
            s.setRemoveWhenFarAway(false);
            s.setInvulnerable(true);

            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            ItemMeta meta = skull.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Soul");
            skull.setItemMeta(meta);
            s.getEquipment().setHelmet(skull, true);

            s.setCustomName(ChatColor.LIGHT_PURPLE + "Soul");
            s.setCustomNameVisible(true);
            s.setGlowing(true);

            s.getPersistentDataContainer().set(SOUL_KEY, PersistentDataType.STRING, SoulTome.encode(fragment));
        });

        BukkitRunnable aura = new BukkitRunnable() {
            /**
             * Last visibility state sent per player. Keyed by the Player instance (weakly
             * held) so rejoining players are treated as unseen again and entries clean
             * themselves up on logout.
             */
            private final WeakHashMap<Player, Boolean> visibilityState = new WeakHashMap<>();

            @Override
            public void run() {
                if (!stand.isValid() || stand.isDead()) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, stand.getLocation().add(0, 0.35, 0), 2, 0.3, 0.3, 0.3, PURPLE);
                updateVisibility();
                collectNearby();
            }

            /**
             * Souls are only visible to players carrying a Soul Tome: everyone else gets
             * the skull hidden via {@code Player#hideEntity}. Packets are only sent when a
             * player's visibility actually changes.
             */
            private void updateVisibility() {
                DMain plugin = DMain.getInstance();
                if (plugin == null) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    boolean shouldSee = SoulTome.findTome(player) != null;
                    Boolean known = visibilityState.get(player);
                    if (known != null && known == shouldSee) {
                        continue;
                    }
                    try {
                        if (shouldSee) {
                            player.showEntity(plugin, stand);
                        } else {
                            player.hideEntity(plugin, stand);
                        }
                        visibilityState.put(player, shouldSee);
                    } catch (Exception ignored) {
                    }
                }
            }

            /**
             * Soul Collector passive: any tome-holding Support within
             * {@link #COLLECT_RADIUS} collects the soul on contact. First eligible player
             * wins; the skull is removed by a successful capture.
             */
            private void collectNearby() {
                for (Entity entity : world.getNearbyEntities(stand.getLocation(), COLLECT_RADIUS, COLLECT_RADIUS,
                        COLLECT_RADIUS)) {
                    if (!(entity instanceof Player player)) {
                        continue;
                    }
                    if (tryCapture(player, stand)) {
                        return;
                    }
                }
            }
        };
        aura.runTaskTimer(DMain.getInstance(), 0L, 5L);

        BukkitRunnable despawn = new BukkitRunnable() {
            @Override
            public void run() {
                aura.cancel();
                stand.remove();
            }
        };
        despawn.runTaskLater(DMain.getInstance(), DESPAWN_SECONDS * 20L);
        return stand;
    }

    /**
     * Attempts to store the skull's soul in the player's Soul Tome. Runs the full
     * capture validation (Support class, tome present, tier gate, capacity).
     * Returns {@code true} when the soul was captured (the skull is removed);
     * {@code false} otherwise.
     */
    public static boolean tryCapture(Player player, Entity skull) {
        SoulFragment fragment = getSoul(skull);
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
            player.sendMessage(ChatColor.RED + "This soul is too powerful for your Support level to capture.");
            return false;
        }

        int capacity = SummonStats.capacityForLevel(level);
        if (SoulTome.countSouls(tome) >= capacity) {
            player.sendMessage(ChatColor.RED + "Your Soul Tome is full (max " + capacity + ").");
            return false;
        }

        SoulTome.addSoul(tome, fragment, capacity);
        skull.remove();
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 1.6f);
        player.sendMessage(ChatColor.GREEN + "Captured " + fragment.mobType().name().toLowerCase().replace('_', ' ')
                + " soul (" + (fragment.tier().name().toLowerCase()) + "). " + ChatColor.GRAY
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
}