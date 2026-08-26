package dev.bukkit.event.bukkitListeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;

import dev.bukkit.ability.behavior.ShadowWeaverBehavior;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.entity.MobRPGEntity;
import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.bukkit.item.BowArrowManager;
import dev.bukkit.summon.SoulSkull;
import dev.bukkit.summon.SummonedMobRPGEntity;
import dev.bukkit.utils.BackstabUtils;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.bukkit.utils.LifestealUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.event.EventAction;
import dev.core.event.EventActionAbstract;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.event.impl.TickEvent;
import dev.core.stat.StatType;

public class CombatListener implements Listener {

    private Plugin plugin;

    public CombatListener(Plugin plugin) {
        this.plugin = plugin;
    }

    private final Random random = new Random();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private static final long DAMAGE_COOLDOWN = 100; // Prevent spam

    // Damage Indicator - Credit to Claude

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
        TextDisplay textDisplay = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(spawnLoc,
                TextDisplay.class, display -> {
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

    /**
     * Healing indicator: mirrors the damage indicator pipeline (TickEvent-driven
     * float animation with a late fade-out), but the text size and the on-screen
     * duration grow with the healed amount so big heals read as such.
     */
    public void showHealingIndicator(Location location, double healing) {
        if (healing <= 0.001) {
            return; // Don't show indicators for empty heals
        }

        World world = location.getWorld();
        if (world == null)
            return;

        Location spawnLoc = location.clone().add(random.nextGaussian() * 0.3, 2.0 + random.nextDouble() * 0.3,
                random.nextGaussian() * 0.3);

        float scale = healIndicatorScale(healing);
        int duration = healIndicatorDurationTicks(healing);

        TextDisplay textDisplay = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(spawnLoc,
                TextDisplay.class, display -> {
                    display.setText(formatHealText(healing));
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setSeeThrough(false);
                    display.setGravity(false);
                    display.setInvulnerable(true);

                    Transformation transformation = display.getTransformation();
                    transformation.getScale().set(scale, scale, scale);
                    display.setTransformation(transformation);

                });

        animateHealing(textDisplay, scale, duration);
    }

    /**
     * Display scale grows with the healed amount (~1.4 at 10 HP, capped at 2.4).
     */
    private float healIndicatorScale(double healing) {
        return (float) Math.min(2.4f, 1.0f + healing / 25.0);
    }

    /**
     * Lifetime in ticks grows with the healed amount (30 at small heals, capped at
     * 70).
     */
    private int healIndicatorDurationTicks(double healing) {
        return (int) Math.min(70, 30 + healing / 2);
    }

    private String formatHealText(double healing) {
        String healStr = String.format("%.2f", healing);

        if (healStr.indexOf('.') > 0) {
            healStr = healStr.replaceAll("0+$", ""); // remove trailing zeros
            healStr = healStr.replaceAll("\\.$", ""); // remove trailing decimal if left
        }

        return "§a+ " + healStr + " ❤";
    }

    private void animateHealing(TextDisplay textDisplay, float initialScale, int duration) {
        EventAction<TickEvent> eventAction = new EventActionAbstract<>(TickEvent.class) {
            private float ticks = 0;
            private final Location startLoc = textDisplay.getLocation().clone();

            @Override
            public void onAction(TickEvent tickEvent) {
                if (!textDisplay.isValid() || ticks >= duration) {
                    textDisplay.remove();
                    BukkitEventBus.getInstance().unsubscribe(this);
                    return;
                }

                // Same gentle float as normal damage numbers
                animateNormal(textDisplay, ticks, startLoc);

                // Fade out effect in the last quarter of animation
                int fadeStart = (int) (duration * 0.75);
                if (ticks >= fadeStart) {
                    Transformation transformation = textDisplay.getTransformation();
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

    /**
     * Impact sound for a landed projectile hit (arrows, tridents, fireballs, the
     * Bonemerang). Normal hits play the classic {@code ENTITY_ARROW_HIT} thock at
     * ~0.8 pitch; crits layer a deeper {@code ITEM_TRIDENT_HIT} thud with an
     * experience-orb chime on top. Played at the victim's location so the impact
     * feels spatial.
     */
    public void playProjectileHitSound(Entity victim, DamageResult result) {
        Location loc = victim.getLocation();
        if (victim instanceof Player) {
            return;
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.79f + random.nextFloat(0.1f));
        if (result == DamageResult.CRIT) {
            loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_HIT, 0.9f, 0.6f + random.nextFloat(0.1f));
            loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f + random.nextFloat(0.2f));
        }
    }

    public static void applyProjectileDamageScaling(EntityDamageByEntityEvent event, Projectile projectile,
            LivingEntity shooter) {
        Double stored = projectile.getPersistentDataContainer().get(BowArrowManager.ARROW_DAMAGE_KEY,
                PersistentDataType.DOUBLE);
        if (stored != null && stored > 0) {
            event.setDamage(stored);
            return;
        }
        EntityManager.getInstance().getEntity(shooter.getUniqueId()).ifPresent(attacker -> {
            double multiplier = attacker.getProjectileDamageMultiplier();
            if (multiplier != 1.0) {
                event.setDamage(event.getDamage() * multiplier);
            }
        });
    }

    /**
     * Whether the given projectile/melee damager and victim are both on the player
     * team (players or player-owned summons), i.e. an allied hit that must never
     * land. A non-living damager (e.g. a block update) is not an allied attacker.
     */
    private static boolean isPlayerTeamDamage(Entity damager, Entity victim) {
        Entity effective = damager;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            effective = shooter;
        }
        return CombatRelation.isPlayerTeam(effective) && CombatRelation.isPlayerTeam(victim);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        // Collectibles are never combat targets: a soul skull (armor stand)
        // must survive every damage source, including environment/AoE damage.
        if (SoulSkull.isSoulSkull(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        // Ignore negligible (~0.001) vanilla damage pokes: BukkitPlayerEntity
        // still uses this for the player hurt reaction, and it must never
        // re-enter dealRPGDamage (which would recurse until the stack
        // overflows). Mob/boss playHitReaction no longer pokes at all.
        if (event.getDamage() <= 0.002) {
            return;
        }
        EntityManager.getInstance().getEntity(event.getEntity().getUniqueId()).ifPresentOrElse(entity -> {
            if (!entity.isAlive()) {
                return;
            }
            RPGDamageResult rpgDamage = entity.dealRPGDamage(null, entity, event.getDamage(), DamageType.PHYSICAL);
            showPhysicalDamage(event.getEntity().getLocation(), rpgDamage.getDamage(), rpgDamage.getResult());
            event.setDamage(DamageUtils.RPG_HANDLED_ENTITY);
        }, () -> {

            showPhysicalDamage(event.getEntity().getLocation(), event.getDamage(), DamageResult.NORMAL);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamagedByEntity(EntityDamageByEntityEvent event) {
        // Soul skulls are collectibles, not combat entities: no mob or player
        // attack (melee, projectile, sweep, ...) may damage or remove them.
        if (SoulSkull.isSoulSkull(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        // Orb shroud blocks AGGRO only, not damage (design decision): a shrouded
        // player is untargetable, but any hit that still connects — AoE splash,
        // an already-swung melee — lands normally.
        if (event.getDamage() <= 0.002) {
            return;
        }

        // No friendly fire: players and their summons are on the same team and
        // can never damage each other through any vanilla damage source - melee,
        // sweep attacks, or projectiles fired by a player (shift-clicked arrows,
        // tridents, ...). The guard runs first so no indicator, knockback or hit
        // reaction ever leaks through. Projectile damagers are unwrapped to the
        // shooter.
        if (isPlayerTeamDamage(event.getDamager(), event.getEntity())) {
            event.setCancelled(true);
            return;
        }

        // Projectile attacks (arrows, tridents, fireballs, ...) amplify their
        // damage when the shooter carries projectile damage bonuses (e.g. the
        // Basic Archer Set bonus). This happens before the RPG pipeline so both
        // RPG entities and vanilla mobs take the boosted damage. Player-fired
        // projectiles carry their full RPG-scaled damage (shooter ATTACK_DAMAGE
        // x projectile multiplier, stamped by BowArrowManager at release) in
        // their PDC; that overrides the vanilla projectile damage entirely.
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity shooter) {
            applyProjectileDamageScaling(event, projectile, shooter);
        }

        // Only projectile strikes get the added impact/crit sound layers; melee
        // hits keep the hurt reaction alone.
        boolean projectileHit = event.getDamager() instanceof Projectile;

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

                event.setDamage(damager.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                        System.currentTimeMillis()));
                // Assassin set passive: bonus damage on melee hits from behind.
                boolean plungeStrike = false;
                if (event.getCause() == DamageCause.ENTITY_ATTACK
                        || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {
                    event.setDamage(event.getDamage() * BackstabUtils.backstabMultiplier(damager, event.getEntity()));
                    // Assassin staff synergy: 1.5x melee damage on the first hit
                    // within 3s of dropping off a shadow platform.
                    double plunge = ShadowWeaverBehavior.consumePlungeMultiplier(damager.getUuid());
                    if (plunge > 1.0) {
                        event.setDamage(event.getDamage() * plunge);
                        plungeStrike = true;
                    }
                }
                // Case 1: Both attacker and victim are RPG entities
                // 'damager' is the RPG entity dealing damage
                // 'entity' is the RPG entity receiving damage
                RPGDamageResult rpgDamage = entity.dealRPGDamage(damager, entity, event.getDamage(),
                        DamageType.PHYSICAL);
                // Lifesteal heals on landed melee auto-attacks only (never on
                // ability/projectile damage).
                if ((event.getCause() == DamageCause.ENTITY_ATTACK
                        || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK)
                        && rpgDamage.getResult() != DamageResult.DENY) {
                    LifestealUtils.applyLifesteal(damager, rpgDamage.getDamage());
                }
                if (plungeStrike) {
                    showPhysicalDamage(event.getEntity().getLocation(), rpgDamage.getDamage(), DamageResult.CRIT);
                    spawnCritParticles(event.getEntity());
                } else {
                    showPhysicalDamage(event.getEntity().getLocation(), rpgDamage.getDamage(), rpgDamage.getResult());
                }
                if (projectileHit && rpgDamage.getResult() != DamageResult.DENY) {
                    playProjectileHitSound(event.getEntity(), rpgDamage.getResult());
                }
                // A dungeon mob that just took a hit from a player-owned summon
                // retargets onto it, so summons can actually tank: otherwise the
                // mob's vanilla AI keeps chasing the nearest player and never
                // acknowledges the summon attacking it.
                if (damager instanceof SummonedMobRPGEntity summon && entity instanceof MobRPGEntity mobRpg
                        && mobRpg.getVanilla() instanceof Mob vanillaMob && summon.getVanilla().isValid()) {
                    vanillaMob.setTarget(summon.getVanilla());
                }
            }, () -> {
                // Case 2: Victim is RPG entity, damager is NOT managed by RPG (e.g., vanilla
                // mob or player)
                // 'entity' is the RPG entity receiving damage
                // attacker is null
                RPGDamageResult rpgDamage = entity.dealRPGDamage(null, entity, event.getDamage(), DamageType.PHYSICAL);
                showPhysicalDamage(event.getEntity().getLocation(), rpgDamage.getDamage(), rpgDamage.getResult());
                if (projectileHit && rpgDamage.getResult() != DamageResult.DENY) {
                    playProjectileHitSound(event.getEntity(), rpgDamage.getResult());
                }
            });

            // Prevent double damage; actual RPG system handles it. The ~0 stamp
            // is the RPG_HANDLED_ENTITY that passive-ability behaviors
            // recognize as a genuine swing (see DamageUtils.isChargeableHit).
            event.setDamage(DamageUtils.RPG_HANDLED_ENTITY);

        }, () -> {
            // Case 3: Victim is NOT an RPG entity (vanilla entity)
            // Just show normal physical damage without RPG logic
            EntityManager.getInstance().getEntity(event.getDamager().getUniqueId()).ifPresentOrElse(damager -> {
                if (!damager.isAlive()) {
                    event.setCancelled(true);
                    return;
                }

                // DAMAGER is an RPG ENtity
                boolean plungeActive = false;
                if (event.getCause() == DamageCause.ENTITY_ATTACK
                        || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {

                    boolean allowed = damager.canAttack();
                    if (!allowed) {
                        event.setCancelled(true);
                        return;
                    }
                    damager.recordAttack();
                    // ONLY SET ON AUTO ATTACKS
                    event.setDamage(damager.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                            System.currentTimeMillis()));
                    // Assassin set passive: bonus damage on melee hits from behind.
                    event.setDamage(event.getDamage() * BackstabUtils.backstabMultiplier(damager, event.getEntity()));
                    // Assassin staff synergy: 1.5x melee damage on the first hit
                    // within 3s of dropping off a shadow platform.
                    double plunge = ShadowWeaverBehavior.consumePlungeMultiplier(damager.getUuid());
                    if (plunge > 1.0) {
                        event.setDamage(event.getDamage() * plunge);
                        plungeActive = true;
                    }
                }

                // Lifesteal heals on landed melee auto-attacks against
                // unregistered (vanilla) victims too; the vanilla pipeline owns
                // the damage, so use its final amount.
                if ((event.getCause() == DamageCause.ENTITY_ATTACK
                        || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) && !event.isCancelled()) {
                    LifestealUtils.applyLifesteal(damager, event.getFinalDamage());
                }

                double critChance = damager.getStatEngineAdapter().getCurrentValue(StatType.CRIT_CHANCE,
                        System.currentTimeMillis());
                if (plungeActive || new Random().nextInt(101) < critChance) {

                    // Apply crit modifier

                    event.setDamage(event.getDamage() * 1.75);
                    showPhysicalDamage(event.getEntity().getLocation(), event.getFinalDamage(), DamageResult.CRIT);
                    if (plungeActive) {
                        spawnCritParticles(event.getEntity());
                    }
                    if (projectileHit) {
                        playProjectileHitSound(event.getEntity(), DamageResult.CRIT);
                    }
                } else {
                    showPhysicalDamage(event.getEntity().getLocation(), event.getFinalDamage(), DamageResult.NORMAL);
                    if (projectileHit) {
                        playProjectileHitSound(event.getEntity(), DamageResult.NORMAL);
                    }
                }
            }, () -> {

                showPhysicalDamage(event.getEntity().getLocation(), event.getFinalDamage(), DamageResult.NORMAL);
            });

        });

        if (event.getEntity() instanceof LivingEntity le && !event.isCancelled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                DamageUtils.updateName(le);
            }, 1L);
        }
    }

    private void spawnCritParticles(Entity victim) {
        Location center = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
        victim.getWorld().spawnParticle(Particle.CRIT, center, 24, 0.4, 0.6, 0.4, 0.2);
        victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center, 12, 0.4, 0.6, 0.4, 0.2);
    }
}
