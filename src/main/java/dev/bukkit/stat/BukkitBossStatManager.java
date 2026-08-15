package dev.bukkit.stat;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import dev.core.stat.StatManager;

/**
 * Bridges the core boss health stats to the vanilla entity's health, without
 * the player-specific heart cap. Must not be ticked while the boss is in its
 * defeat sequence.
 */
public class BukkitBossStatManager {

    private final UUID uuid;
    private final StatManager statManager;

    public BukkitBossStatManager(Entity entity, StatManager statManager) {
        if (!(entity instanceof LivingEntity)) {
            throw new IllegalArgumentException("BossStatManager can't accept non living entity");
        }
        this.uuid = entity.getUniqueId();
        this.statManager = statManager;
    }

    public void tick(long now, Runnable onDeath, double currentHealth, double maxHealth) {
        LivingEntity entity = (LivingEntity) Bukkit.getEntity(uuid);
        if (entity == null || entity.isDead()) {
            return;
        }
        if (maxHealth <= 0) {
            return;
        }

        AttributeInstance healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxBukkitHealth = healthAttr.getValue();

        double calculatedHealth = (Math.min(1.0, currentHealth / maxHealth)) * maxBukkitHealth;

        if (currentHealth <= 0) {
            onDeath.run();
        } else {
            entity.setHealth(Math.max(0.5, calculatedHealth));
        }
    }
}
