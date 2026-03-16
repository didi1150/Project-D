package dev.bukkit.event.bukkitListeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import dev.bukkit.event.BukkitEventBus;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.event.EventAction;
import dev.core.event.EventActionAbstract;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.event.impl.TickEvent;
import dev.core.event.impl.ToggleCombatEvent;
import dev.core.stat.StatType;

public class CombatListener {

    private Plugin plugin;
    private boolean allowCombat = true;

    public CombatListener(Plugin plugin, EventBusInterface eventBusInterface) {
        this.plugin = plugin;
        damageSub = new EventAction<EntityDamageEvent>(this::onDamage, EntityDamageEvent.class,
                EventAction.LOWEST_PRIORITY);
        eventBusInterface.subscribe(damageSub);
        entityDamageSub = new EventAction<EntityDamageByEntityEvent>(this::onDamagedByEntity,
                EntityDamageByEntityEvent.class, EventAction.LOWEST_PRIORITY);
        eventBusInterface.subscribe(entityDamageSub);
        eventBusInterface.subscribe(
                new EventAction<ToggleCombatEvent>(e -> allowCombat = e.isToggled(), ToggleCombatEvent.class));
    }

    private final Random random = new Random();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private static final long DAMAGE_COOLDOWN = 100; // Prevent spam
    private EventAction<EntityDamageEvent> damageSub;
    private EventAction<EntityDamageByEntityEvent> entityDamageSub;

    // Main method to show damage indicators
    public void showDamageIndicator(Location location, double damage, DamageType type, DamageResult result) {
        if (result == DamageResult.DENY || damage <= 0.001) {
            return; // Don't show indicators for denied damage
        }

        World world = location.getWorld();
        if (world == null)
            return;

        // Add randomness to position to prevent overlap
        Location spawnLoc = location.clone().add(random.nextGaussian() * 0.5, 2.0 + random.nextDouble() * 0.5,
                random.nextGaussian() * 0.5);

        // Create Text Display entity
        TextDisplay textDisplay = world.spawn(spawnLoc, TextDisplay.class, display -> {
            display.setText(formatDamageText(damage, type, result));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setGravity(false);
            display.setInvulnerable(true);

            // Set size based on damage type
            Transformation transformation = display.getTransformation();
            float scale = result == DamageResult.CRIT ? 2.0f : 1.5f;
            transformation.getScale().set(scale, scale, scale);
            display.setTransformation(transformation);
        });

        // Animate the damage indicator
        animateTextDisplay(textDisplay, result);
    }

    private String formatDamageText(double damage, DamageType type, DamageResult result) {
        // Format with 2 decimals
        String damageStr = String.format("%.2f", damage);

        // Trim trailing zeros and decimal point if not needed
        if (damageStr.indexOf('.') > 0) {
            damageStr = damageStr.replaceAll("0+$", ""); // remove trailing zeros
            damageStr = damageStr.replaceAll("\\.$", ""); // remove trailing decimal if left
        }

        if (result == DamageResult.CRIT) {
            return formatCriticalDamage(damageStr);
        } else {
            return getTypeColor(type) + getTypeSymbol(type) + " " + damageStr;
        }
    }

    private String getTypeColor(DamageType type) {
        switch (type) {
        case PHYSICAL:
            return "§c"; // Red
        case MAGIC:
            return "§b"; // Aqua/Blue
        case TRUE:
            return "§f"; // White
        default:
            return "§c";
        }
    }

    private String getTypeSymbol(DamageType type) {
        switch (type) {
        case PHYSICAL:
            return "⚔"; // Sword
        case MAGIC:
            return "✦"; // Star
        case TRUE:
            return "◆"; // Diamond
        default:
            return "⚔";
        }
    }

    private String formatCriticalDamage(String damage) {
        // Rainbow effect for critical hits
        StringBuilder rainbow = new StringBuilder();
        String[] colors = { "§c", "§6", "§e", "§a", "§b", "§d" };

        rainbow.append("§l✦ "); // Bold star
        for (int i = 0; i < damage.length(); i++) {
            char c = damage.charAt(i);
            String color = colors[i % colors.length];
            rainbow.append(color).append(c);
        }
        rainbow.append(" §l✦");

        return rainbow.toString();
    }

    private void animateTextDisplay(TextDisplay textDisplay, DamageResult result) {
        EventAction<TickEvent> eventAction = new EventActionAbstract<>(TickEvent.class) {
            private float ticks = 0;
            private final Location startLoc = textDisplay.getLocation().clone();
            private final int duration = result == DamageResult.CRIT ? 50 : 40; // Crits last longer

            @Override
            public void onAction(TickEvent tickEvent) {
                if (!textDisplay.isValid() || ticks >= duration) {
                    textDisplay.remove();
                    BukkitEventBus.getInstance().unsubscribe(this);
                    return;
                }

                // Different animations based on result
                if (result == DamageResult.CRIT) {
                    animateCritical(textDisplay, ticks, startLoc);
                } else {
                    animateNormal(textDisplay, ticks, startLoc);
                }

                // Fade out effect in the last quarter of animation
                int fadeStart = (int) (duration * 0.75);
                if (ticks >= fadeStart) {
                    Transformation transformation = textDisplay.getTransformation();
                    float initialScale = result == DamageResult.CRIT ? 2.0f : 1.5f;
                    float fadeProgress = (float) (ticks - fadeStart) / (duration - fadeStart);
                    float scale = initialScale * (1.0f - fadeProgress * 0.8f); // Fade to 20% size
                    transformation.getScale().set(scale, scale, scale);
                    textDisplay.setTransformation(transformation);
                }

                ticks += tickEvent.getTickDelta();
            }
        };
        BukkitEventBus.getInstance().subscribe(eventAction);
    }

    private void animateNormal(TextDisplay textDisplay, float ticks, Location startLoc) {
        // Gentle upward float with slight side-to-side motion
        double y = Math.sin(ticks * 0.1) * 0.05 + (ticks * 0.04);
        double x = Math.sin(ticks * 0.15) * 0.2;
        double z = Math.cos(ticks * 0.15) * 0.2;

        Location newLoc = startLoc.clone().add(x, y, z);
        textDisplay.teleport(newLoc);
    }

    private void animateCritical(TextDisplay textDisplay, float ticks, Location startLoc) {
        // More dramatic animation for critical hits
        double y = Math.sin(ticks * 0.15) * 0.1 + (ticks * 0.06);
        double x = Math.sin(ticks * 0.2) * 0.4;
        double z = Math.cos(ticks * 0.2) * 0.4;

        // Bounce
        if (ticks < 10) {
            y += Math.sin(ticks * 0.5) * 0.3;
        }

        Location newLoc = startLoc.clone().add(x, y, z);
        textDisplay.teleport(newLoc);
    }

    // Method for healing indicators (bonus feature)
    public void showHealingIndicator(Location location, double healing) {
        World world = location.getWorld();
        if (world == null)
            return;

        Location spawnLoc = location.clone().add(random.nextGaussian() * 0.3, 2.0 + random.nextDouble() * 0.3,
                random.nextGaussian() * 0.3);

        TextDisplay textDisplay = world.spawn(spawnLoc, TextDisplay.class, display -> {
            String healText = "§a+ " + String.format("%.0f", healing) + " ❤";
            display.setText(healText);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setGravity(false);
            display.setInvulnerable(true);

            Transformation transformation = display.getTransformation();
            transformation.getScale().set(1.2f, 1.2f, 1.2f);
            display.setTransformation(transformation);
        });

        animateHealing(textDisplay);
    }

    private void animateHealing(TextDisplay textDisplay) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final Location startLoc = textDisplay.getLocation().clone();
            private final int duration = 30;

            @Override
            public void run() {
                if (!textDisplay.isValid() || ticks >= duration) {
                    textDisplay.remove();
                    this.cancel();
                    return;
                }

                // Gentle upward float for healing
                double y = ticks * 0.05;
                Location newLoc = startLoc.clone().add(0, y, 0);
                textDisplay.teleport(newLoc);

                // Fade out in last third
                if (ticks >= duration * 0.67) {
                    Transformation transformation = textDisplay.getTransformation();
                    float fadeProgress = (float) (ticks - duration * 0.67) / (duration * 0.33f);
                    float scale = 1.2f * (1.0f - fadeProgress * 0.8f);
                    transformation.getScale().set(scale, scale, scale);
                    textDisplay.setTransformation(transformation);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Damage spam prevention
    public boolean canShowDamage(Entity entity) {
        long now = System.currentTimeMillis();
        Long lastTime = lastDamageTime.get(entity.getUniqueId());
        if (lastTime != null && now - lastTime < DAMAGE_COOLDOWN) {
            return false;
        }
        lastDamageTime.put(entity.getUniqueId(), now);
        return true;
    }

    // Cleanup method for plugin disable
    public void cleanup() {
        lastDamageTime.clear();
    }

    // Example usage methods
    public void showPhysicalDamage(Location location, double damage, DamageResult result) {
        showDamageIndicator(location, damage, DamageType.PHYSICAL, result);
    }

    public void showMagicDamage(Location location, double damage, DamageResult result) {
        showDamageIndicator(location, damage, DamageType.MAGIC, result);
    }

    public void showTrueDamage(Location location, double damage) {
        showDamageIndicator(location, damage, DamageType.TRUE, DamageResult.NORMAL);
    }

    public void onDamage(EntityDamageEvent event) {
        if (!allowCombat) {
            event.setCancelled(true);
            plugin.getLogger().info("Blocked combat");
            return;
        }
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        EntityManager.getInstance().getEntity(event.getEntity().getUniqueId()).ifPresentOrElse(entity -> {
            if (!entity.isAlive()) {
                return;
            }
            RPGDamageResult rpgDamage = entity.dealRPGDamage(null, entity, event.getDamage(), DamageType.PHYSICAL);
            showPhysicalDamage(event.getEntity().getLocation(), rpgDamage.getDamage(), rpgDamage.getResult());
            event.setDamage(0.001);
        }, () -> {

            showPhysicalDamage(event.getEntity().getLocation(), event.getDamage(), DamageResult.NORMAL);
        });
    }

    public void onDamagedByEntity(EntityDamageByEntityEvent event) {
        checkArrowOnGhost(event);
        if (!allowCombat) {
            event.setCancelled(true);
            plugin.getLogger().info("Blocked combat");
            return;
        }
        if (event.getDamage() <= 0.002) {
            return;
        }

        // Check if the entity being damaged is an RPG-managed entity
        EntityManager.getInstance().getEntity(event.getEntity().getUniqueId()).ifPresentOrElse(entity -> {

            // If the RPG entity is not alive, do nothing
            if (!entity.isAlive()) {
                event.setCancelled(true);
                return;
            }

            // Check if the damager is an RPG-managed entity
            EntityManager.getInstance().getEntity(event.getDamager().getUniqueId()).ifPresentOrElse(damager -> {
                if (!damager.isAlive()) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getCause() == DamageCause.ENTITY_ATTACK
                        || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {

                    boolean allowed = damager.canAttack();
                    if (!allowed) {
                        event.setCancelled(true);
                        return;
                    }
                    damager.recordAttack();
                }

                event.setDamage(
                        damager.getStatManager().getCurrentValue(StatType.ATTACK_DAMAGE, System.currentTimeMillis()));
                // Case 1: Both attacker and victim are RPG entities
                // 'damager' is the RPG entity dealing damage
                // 'entity' is the RPG entity receiving damage
                RPGDamageResult rpgDamage = entity.dealRPGDamage(damager, entity, event.getDamage(),
                        DamageType.PHYSICAL);
                showPhysicalDamage(event.getEntity().getLocation(), rpgDamage.getDamage(), rpgDamage.getResult());
            }, () -> {
                // Case 2: Victim is RPG entity, damager is NOT managed by RPG (e.g., vanilla
                // mob or player)
                // 'entity' is the RPG entity receiving damage
                // attacker is null
                RPGDamageResult rpgDamage = entity.dealRPGDamage(null, entity, event.getDamage(), DamageType.PHYSICAL);
                showPhysicalDamage(event.getEntity().getLocation(), rpgDamage.getDamage(), rpgDamage.getResult());
            });

            // Prevent double damage; actual RPG system handles it
            event.setDamage(0.001);

        }, () -> {
            // Case 3: Victim is NOT an RPG entity (vanilla entity)
            // Just show normal physical damage without RPG logic
            EntityManager.getInstance().getEntity(event.getDamager().getUniqueId()).ifPresentOrElse(damager -> {
                if (!damager.isAlive()) {
                    event.setCancelled(true);
                    return;
                }

                // DAMAGER is an RPG ENtity
                if (event.getCause() == DamageCause.ENTITY_ATTACK
                        || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {

                    boolean allowed = damager.canAttack();
                    if (!allowed) {
                        event.setCancelled(true);
                        return;
                    }
                    damager.recordAttack();
                    // ONLY SET ON AUTO ATTACKS
                    event.setDamage(damager.getStatManager().getCurrentValue(StatType.ATTACK_DAMAGE,
                            System.currentTimeMillis()));
                }

                double critChance = damager.getStatManager().getCurrentValue(StatType.CRIT_CHANCE,
                        System.currentTimeMillis());
                if (new Random().nextInt(101) < critChance) {

                    // Apply crit modifier

                    event.setDamage(event.getDamage() * 1.75);
                    showPhysicalDamage(event.getEntity().getLocation(), event.getFinalDamage(), DamageResult.CRIT);
                } else {
                    showPhysicalDamage(event.getEntity().getLocation(), event.getFinalDamage(), DamageResult.NORMAL);
                }
            }, () -> {

                showPhysicalDamage(event.getEntity().getLocation(), event.getFinalDamage(), DamageResult.NORMAL);
            });

        });
    }

    private void checkArrowOnGhost(EntityDamageByEntityEvent event) {

        Entity entityDamager = event.getDamager();
        Entity entityDamaged = event.getEntity();

        if (entityDamager instanceof Arrow) {
            if (entityDamaged instanceof Player && ((Arrow) entityDamager).getShooter() instanceof Player) {
                Arrow arrow = (Arrow) entityDamager;

                Vector velocity = arrow.getVelocity();

                Player shooter = (Player) arrow.getShooter();
                Player damaged = (Player) entityDamaged;

                if (EntityManager.getInstance().isDead(damaged.getUniqueId())) {
                    damaged.teleport(entityDamaged.getLocation().add(0, 5, 0));
                    damaged.setFlying(true);

                    Arrow newArrow = shooter.launchProjectile(Arrow.class);
                    newArrow.setShooter(shooter);
                    newArrow.setVelocity(velocity);
                    event.setCancelled(true);
                    arrow.remove();
                }
            }
        }

    }
}
