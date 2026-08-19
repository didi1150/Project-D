package dev.bukkit.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import dev.bukkit.entity.VanillaEntityMeta;
import dev.bukkit.summon.SoulSkull;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;

public class DamageUtils {

    public static void damageMob(LivingEntity le, double damage, LivingEntity source) {
        // Soul skulls are collectibles, not combat entities: AoE abilities that
        // fall back to this vanilla-damage path must never hurt or remove them.
        if (SoulSkull.isSoulSkull(le)) {
            return;
        }
        le.damage(0.001, source);
        if (le.getHealth() > 0) {
            le.setHealth(Math.max(le.getHealth() - damage, 0));
            Bukkit.getPluginManager().callEvent(new EntityDamageByEntityEvent(source, le, DamageCause.CUSTOM,
                    DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), damage));
        }
        updateName(le);
    }

    public static void updateName(LivingEntity entity) {
        if (!entity.hasMetadata("VANILLA_META")) {
            return;
        }

        VanillaEntityMeta meta = (VanillaEntityMeta) entity.getMetadata("VANILLA_META").get(0).value();

        double[] health = rpgHealthOf(entity);

        String color;
        switch (meta.getRelation()) {
        case FRIENDLY -> color = "§a"; // green
        case NEUTRAL -> color = "§e"; // yellow
        case HOSTILE -> color = "§c"; // red
        default -> color = "§f";
        }

        String typePart = meta.getDisplayName() != null
                ? ChatColor.translateAlternateColorCodes('&', meta.getDisplayName())
                : entity.getType().name();
        String name = color + "[Lvl " + meta.getLevel() + "] " + typePart + " [❤] " + Math.round(health[0]) + "/"
                + Math.round(health[1]);

        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
    }

    /**
     * Resolves the health shown for a spawned entity: the RPG entity's
     * {@code HEALTH_RESOURCE}/{@code HEALTH_MAX} when one is registered for this
     * entity's uuid (bosses, RPG-managed mobs), otherwise the vanilla entity's
     * health. Shown in the {@code [❤] current/max} name format.
     */
    private static double[] rpgHealthOf(LivingEntity entity) {
        double maxHp = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        Optional<RPGEntity> rpg = EntityManager.getInstance().getEntity(entity.getUniqueId());
        if (rpg.isPresent()) {
            return new double[] { rpg.get().getHealth(), rpg.get().getMaxHealth() };
        }
        return new double[] { entity.getHealth(), maxHp };
    }

    public static void playEpicHoleAnimation(Plugin plugin, Location center, int baseRadius, int depth,
            Set<Player> viewers) {
        playEpicHoleAnimation(plugin, center, baseRadius, depth, 6, 10, viewers);
    }

    public static void playEpicHoleAnimation(Plugin plugin, Location center, int baseRadius, int depth, int baseDelay,
            int upwardLifespan, Set<Player> viewers) {
        World world = center.getWorld();
        if (world == null)
            return;

        // --- Parameters you can tweak ---
//        int baseDelay = 6;  ticks between layers
//        int upwardLifespan = 10;  ticks before "popped" blocks vanish
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            // Precompute layers (circular radius decreases slightly per depth)
            List<List<Block>> layers = new ArrayList<>();
            for (int y = 0; y < depth; y++) {
                int radius = Math.max(1, baseRadius - (y / 2)); // smaller radius deeper
                List<Block> layer = new ArrayList<>();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz <= radius * radius) {
                            layer.add(center.clone().add(dx, -y, dz).getBlock());
                        }
                    }
                }
                layers.add(layer);
            }

            new BukkitRunnable() {
                int step = 0;

                @Override
                public void run() {
                    List<Block> layer = layers.get(step);
                    for (Player p : viewers) {
                        for (Block block : layer) {
                            Location loc = block.getLocation();

                            // --- Choose sound by block material ---
                            Sound breakSound = getBreakSound(block.getType());

                            // --- Fake remove the block ---
                            p.sendBlockChange(loc, Material.AIR.createBlockData());

                            // --- Pop upward visual effect ---
                            Location popLoc = loc.clone().add(0.5, 0.5, 0.5);
                            DustOptions dustOptions = getDustOptions(block.getType());
//                            if (currentLayer == 0) {
//                            p.spawnParticle(Particle.DUST, popLoc, 30, // count
//                                    0.4, 0.6, 0.4, // spread
//                                    0.15, dustOptions);
                            //
//                                // Block fragment "flies upward" via temporary falling dust
//                            world.spawnParticle(Particle.FALLING_DUST, popLoc.add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.05,
//                                    block.getBlockData());
                            //
//                                // Create upward flying block animation
                            //
//                                createFlyingBlockAnimation(plugin, world, loc, block.getType());
//                            }

                            // Play sound once per block type (optional: group this per layer)
                            if (breakSound != null) {
                                world.playSound(loc, breakSound, 1.0f, 1.0f);
                            }
                        }
                    }

                    // After upward animation, despawn (keep fake air until final step)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (Player p : viewers) {
                            for (Block block : layer) {
                                p.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
                            }
                        }
                    }, upwardLifespan);

                    // On the final step, remove blocks in the real world
                    if (step == layers.size() - 1) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (List<Block> l : layers) {
                                for (Block block : l) {
                                    block.setType(Material.AIR, false);
                                }
                            }
                        });
                        this.cancel();
                    }
                    step++;
                }
            }.runTaskTimer(plugin, 0, baseDelay);
        });

    }

    // Helper: crude block→sound mapping
    private static Sound getBreakSound(Material mat) {
        if (mat.name().contains("WOOD"))
            return Sound.BLOCK_WOOD_BREAK;
        if (mat.name().contains("STONE"))
            return Sound.BLOCK_STONE_BREAK;
        if (mat == Material.GRASS_BLOCK || mat == Material.DIRT)
            return Sound.BLOCK_GRASS_BREAK;
        if (mat == Material.GLASS || mat == Material.GLASS_PANE)
            return Sound.BLOCK_GLASS_BREAK;
        if (mat == Material.SAND || mat == Material.GRAVEL)
            return Sound.BLOCK_SAND_BREAK;
        return Sound.BLOCK_STONE_BREAK; // default
    }

    // Helper: get dust color based on block material
    private static DustOptions getDustOptions(Material mat) {
        org.bukkit.Color color;

        if (mat.name().contains("WOOD")) {
            color = org.bukkit.Color.fromRGB(139, 69, 19); // Brown
        } else if (mat.name().contains("STONE")) {
            color = org.bukkit.Color.fromRGB(128, 128, 128); // Gray
        } else if (mat == Material.GRASS_BLOCK) {
            color = org.bukkit.Color.fromRGB(34, 139, 34); // Green
        } else if (mat == Material.DIRT) {
            color = org.bukkit.Color.fromRGB(139, 90, 43); // Dark brown
        } else if (mat == Material.GLASS || mat == Material.GLASS_PANE) {
            color = org.bukkit.Color.fromRGB(173, 216, 230); // Light blue
        } else if (mat == Material.SAND) {
            color = org.bukkit.Color.fromRGB(238, 203, 173); // Sandy brown
        } else if (mat == Material.GRAVEL) {
            color = org.bukkit.Color.fromRGB(105, 105, 105); // Dim gray
        } else if (mat.name().contains("ORE")) {
            color = org.bukkit.Color.fromRGB(255, 215, 0); // Gold for ores
        } else if (mat.name().contains("COAL")) {
            color = org.bukkit.Color.fromRGB(64, 64, 64); // Dark gray
        } else if (mat.name().contains("IRON")) {
            color = org.bukkit.Color.fromRGB(192, 192, 192); // Silver
        } else if (mat.name().contains("DIAMOND")) {
            color = org.bukkit.Color.fromRGB(0, 191, 255); // Deep sky blue
        } else if (mat.name().contains("EMERALD")) {
            color = org.bukkit.Color.fromRGB(0, 255, 127); // Spring green
        } else if (mat.name().contains("REDSTONE")) {
            color = org.bukkit.Color.fromRGB(255, 0, 0); // Red
        } else if (mat.name().contains("LAPIS")) {
            color = org.bukkit.Color.fromRGB(70, 130, 180); // Steel blue
        } else {
            color = org.bukkit.Color.fromRGB(128, 128, 128); // Default gray
        }

        return new DustOptions(color, 1.5f); // size 1.5f for visibility
    }

    // Helper: create flying block animation
    private static void createFlyingBlockAnimation(Plugin plugin, World world, Location blockLoc, Material blockType) {
        // Create multiple falling block entities that fly upward and outward
        for (int i = 0; i < 3; i++) { // 3 flying blocks per destroyed block
            Location spawnLoc = blockLoc.clone().add(0.5, 0.1, 0.5);

            // Random upward and outward velocity
            double velX = (Math.random() - 0.5) * 4.0; // -0.4 to 0.4
            double velY = 0.4 + Math.random() * 0.6; // 0.4 to 1.0 upward
            double velZ = (Math.random() - 0.5) * 4.0; // -0.4 to 0.4

            org.bukkit.entity.FallingBlock fallingBlock = world.spawnFallingBlock(spawnLoc,
                    blockType.createBlockData());

            // Set velocity for upward/outward motion
            fallingBlock.setVelocity(new org.bukkit.util.Vector(velX, velY, velZ));

            // Prevent the block from placing when it lands
            fallingBlock.setDropItem(false);
            fallingBlock.setCancelDrop(true);
            fallingBlock.setHurtEntities(false);

            // Schedule removal after flight time
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (fallingBlock.isValid()) {
                    // Create small explosion effect when block disappears
                    Location disappearLoc = fallingBlock.getLocation();
                    world.spawnParticle(Particle.POOF, disappearLoc, 5, 0.1, 0.1, 0.1, 0.02);
                    fallingBlock.remove();
                }
            }, 40 + (int) (Math.random() * 20)); // 2-3 seconds flight time
        }
    }

}
