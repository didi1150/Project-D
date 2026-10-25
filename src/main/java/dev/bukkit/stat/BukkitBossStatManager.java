package dev.bukkit.stat;

import java.util.Objects;
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
    private final LivingEntity entity;
    private final StatManager statManager;

    public BukkitBossStatManager(Entity entity, StatManager statManager) {
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalArgumentException("BossStatManager can't accept non living entity");
        }
        this.uuid = living.getUniqueId();
        this.entity = living;
        this.statManager = Objects.requireNonNull(statManager, "statManager");
    }

    public void tick(long now, Runnable onDeath, double currentHealth, double maxHealth) {
        // Same caveat as BukkitBossEntity#getLivingEntity: isValid() is false
        // while the boss chunk is not entity-ticking, but the entity is still
        // alive and must be kept in sync.
        LivingEntity target = !entity.isDead() ? entity : null;
        if (target == null) {
            Entity resolved = Bukkit.getEntity(uuid);
            if (resolved instanceof LivingEntity living && !living.isDead()) {
                target = living;
            }
        }
        if (target == null) {
            return;
        }
        if (maxHealth <= 0) {
            return;
        }

        AttributeInstance healthAttr = target.getAttribute(Attribute.MAX_HEALTH);
        double maxBukkitHealth = healthAttr.getValue();

        double calculatedHealth = (Math.min(1.0, currentHealth / maxHealth)) * maxBukkitHealth;

        if (currentHealth <= 0) {
            onDeath.run();
        } else {
            target.setHealth(Math.max(0.5, calculatedHealth));
        }
    }
}
