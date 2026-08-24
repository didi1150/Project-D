package dev.core.entity.boss;

import java.util.Optional;

import dev.core.entity.EntityManager;
import dev.core.entity.EntityType;
import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;
import dev.bukkit.utils.StealthRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class FirstAlivePlayerTargetingStrategy implements TargetingStrategy {

    @Override
    public Optional<RPGEntity> selectTarget(RPGMobEntity mob) {
        return EntityManager.getInstance().getAliveEntities().stream()
                .filter(entity -> entity != mob && entity.getEntityType() == EntityType.PLAYER && entity.isAlive())
                .filter(entity -> {
                    // hide shrouded / passive-dodged players from boss targeting
                    try {
                        Player p = Bukkit.getPlayer(entity.getUuid());
                        return p == null || !StealthRegistry.shouldHideFromMob(p);
                    } catch (Exception e) { return true; }
                })
                .findFirst();
    }
}
