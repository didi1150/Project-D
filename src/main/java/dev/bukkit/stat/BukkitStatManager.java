package dev.bukkit.stat;

import java.util.Map.Entry;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;

public class BukkitStatManager {

    private LivingEntity entity;
    private StatManager statManager;

    public BukkitStatManager(Entity entity, StatManager statManager) {
        if (entity instanceof LivingEntity le) {
            this.entity = le;
        } else {
            throw new IllegalArgumentException("Can't accept a non living entity");
        }
        this.statManager = statManager;
    }

    public void tick(long now, Runnable onDeath) {
        for (Entry<StatType, Stat> entry : statManager.getStats().entrySet()) {
            StatType type = entry.getKey();
            Stat stat = entry.getValue();

            switch (type) {
            case ATTACK_SPEED: {
                entity.getAttribute(Attribute.ATTACK_SPEED).getModifiers().clear();
                double current = stat.getCurrent(now);
                entity.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(current > 1024 ? 1024 : current);
                break;
            }

            case MOVE_SPEED: {
                entity.getAttribute(Attribute.MOVEMENT_SPEED).getModifiers().clear();
                double defaultSpeed = 0.1;
                double moveSpeedStat = stat.getCurrent(now);

                // diminishing returns (soft cap at ~400-500)
                double ratio;
                if (moveSpeedStat <= 100) {
                    ratio = moveSpeedStat / 100.0; // linear up to 100
                } else {
                    ratio = 1.0 + (Math.sqrt(moveSpeedStat - 100) / 10.0);
                    // slows down scaling after 100
                }
                entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(defaultSpeed * ratio);
                break;
            }
            default:
                break;
            }
        }

        updatePlayerVanillaHealth(statManager.getCurrentValue(StatType.HEALTH_RESOURCE, now),
                statManager.getCurrentValue(StatType.HEALTH_MAX, now), onDeath);
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
        AttributeInstance healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(vanillaHP);
        }

        // Update current health proportionally
        double healthPercentage = currentHealth / maxHealth;
        double health = vanillaHP * healthPercentage;
        if (health <= 0 || currentHealth <= 0) {
            onDeath.run();
        } else {
            entity.setHealth(health);
        }
    }

}
