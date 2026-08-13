package dev.bukkit.ability;

import org.bukkit.entity.Fireball;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

public class BukkitParticleTestEffect extends Effect {

    public BukkitParticleTestEffect(String cooldownKey) {
        super(null, 1, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, Runnable startCooldown, Runnable resetCooldown) {
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            startCooldown.run();
            playerEntity.getPlayer().get().playEffect(playerEntity.getPlayer().get().getLocation(),
                    org.bukkit.Effect.BLAZE_SHOOT, null);
            playerEntity.getPlayer().get().launchProjectile(Fireball.class);
        }
    }

    @Override
    public void cancel() {

    }

}
