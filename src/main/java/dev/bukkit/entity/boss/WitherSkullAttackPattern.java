package dev.bukkit.entity.boss;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.entity.RPGEntity;
import dev.core.entity.RPGMobEntity;
import dev.core.entity.boss.AttackPattern;
import dev.core.storage.config.ConfigSection;

public class WitherSkullAttackPattern implements AttackPattern {

    private final int skullCount;
    private final long cooldownMillis;
    private long lastShotAt;

    public WitherSkullAttackPattern(ConfigSection params) {
        this.skullCount = params.getInt("attack-count", 1);
        this.cooldownMillis = params.getInt("attack-cooldown-ms", 1000);
    }

    @Override
    public void performAttack(RPGMobEntity mob, Optional<RPGEntity> currentTarget, long now) {
        if (currentTarget.isEmpty() || !(mob instanceof BukkitBossEntity bukkitBoss)) {
            return;
        }
        if (now - lastShotAt < cooldownMillis) {
            return;
        }
        RPGEntity target = currentTarget.get();
        if (!(target instanceof BukkitPlayerEntity playerEntity)) {
            return;
        }
        Optional<Player> player = playerEntity.getPlayer();
        Optional<org.bukkit.entity.LivingEntity> living = bukkitBoss.getLivingEntity();
        if (player.isEmpty() || living.isEmpty()) {
            return;
        }

        Location origin = living.get().getLocation();
        if (origin.getWorld() == null) {
            return;
        }
        for (int i = 0; i < skullCount; i++) {
            Location projectileOrigin = origin.clone().add(0, 1.2, 0);
            Location aim = player.get().getLocation().clone().add(0, 1.0, 0);
            WitherSkull skull = origin.getWorld().spawn(projectileOrigin, WitherSkull.class);
            skull.setCharged(true);
            skull.setDirection(aim.toVector().subtract(projectileOrigin.toVector()).normalize());
        }
        lastShotAt = now;
    }
}
