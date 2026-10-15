package dev.bukkit.entity;

import org.bukkit.entity.LivingEntity;

public class NoOpBehaviour implements MobBehavior {

    @Override
    public void onSpawn(MobRPGEntity mob, LivingEntity vanilla) {
        vanilla.setAI(false);
    }

    @Override
    public void onTick(MobRPGEntity mob, LivingEntity vanilla, long now) {
    }

}
