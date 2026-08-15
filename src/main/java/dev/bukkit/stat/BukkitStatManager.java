package dev.bukkit.stat;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;

public class BukkitStatManager {

    private UUID uuid;
    private StatManager statManager;

    public BukkitStatManager(Entity entity, StatManager statManager) {
        if (!(entity instanceof LivingEntity)) {
            throw new IllegalArgumentException("StatManager can't accept non living entity");
        }
        this.uuid = entity.getUniqueId();
        this.statManager = statManager;
    }

    public void tick(long now, Runnable onDeath, double currentHealth, double maxHealth) {
        LivingEntity entity = (LivingEntity) Bukkit.getEntity(uuid);
        for (Entry<StatType, Stat> entry : statManager.getStats().entrySet()) {
            StatType type = entry.getKey();
            Stat stat = entry.getValue();
            stripVanillaItemModifiers(entity);
            switch (type) {
            case ATTACK_SPEED: {
                double current = stat.getCurrent(now);
                entity.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(current > 1024 ? 1024 : current);
                break;
            }

            case MOVE_SPEED: {
                entity.getAttribute(Attribute.MOVEMENT_SPEED).getModifiers().clear();
                entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(computeMoveSpeed(stat.getCurrent(now)));
                break;
            }
            default:
                break;
            }
        }

        updatePlayerVanillaHealth(currentHealth, maxHealth, onDeath);
    }

    private void stripVanillaItemModifiers(LivingEntity entity) {
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) {
                continue;
            }

            // Remove only modifiers that come from equipment
            for (AttributeModifier mod : new HashSet<>(instance.getModifiers())) {
                if (isVanillaItemModifier(mod)) {
                    instance.removeModifier(mod);
                }
            }
        }
    }

    private boolean isVanillaItemModifier(AttributeModifier mod) {
        return mod.getSlotGroup() != null;
    }

    private double calculateVanillaHearts(double rpgHealth) {
        if (rpgHealth <= 100) {
            // Below or at base health: direct conversion (100 HP = 10 hearts)
            return rpgHealth / 10.0;
        }

        // Above base health: use scaling formula
        double hearts = (100 + (rpgHealth - 100) * 2) / 10.0;

        // Cap at 20 hearts (2 rows maximum)
        return Math.min(hearts, 20.0);
    }

    public void updatePlayerVanillaHealth(double currentHealth, double maxHealth, Runnable onDeath) {
        double vanillaHearts = calculateVanillaHearts(maxHealth);
        double vanillaHP = vanillaHearts * 2; // Minecraft uses half-hearts (20 HP = 10 hearts)

        // Set max health attribute
        AttributeInstance healthAttr = ((LivingEntity) Bukkit.getEntity(uuid)).getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(vanillaHP);
        }

        // Update current health proportionally. current/max must come from the
        // SAME source (the engine-aware values), otherwise a heal that used the
        // engine max (e.g. +item health) is divided by the raw max and pushes
        // the vanilla hp above its mapped maximum.
        double health = vanillaHP * Math.min(1.0, currentHealth / maxHealth);
        if (health <= 0 || currentHealth <= 0) {
            onDeath.run();
        } else {
            ((LivingEntity) Bukkit.getEntity(uuid)).setHealth(health);
        }
    }

    /**
     * Converts the RPG MOVE_SPEED stat to the vanilla {@code Attribute.MOVEMENT_SPEED}
     * value, using the same diminishing-returns curve players use
     * (100 = the base custom speed). Shared by mob spawning and per-entity ticks.
     */
    public static double computeMoveSpeed(double moveSpeedStat) {
        double defaultSpeed = 0.1;
        double ratio;
        if (moveSpeedStat <= 100) {
            ratio = moveSpeedStat / 100.0; // linear up to 100
        } else {
            ratio = 1.0 + (Math.sqrt(moveSpeedStat - 100) / 10.0); // softer after 100
        }
        return defaultSpeed * ratio;
    }

}
