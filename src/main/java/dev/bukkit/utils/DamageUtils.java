package dev.bukkit.utils;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.entity.VanillaEntityMeta;

public class DamageUtils {

    public static void damageMob(LivingEntity le, double damage, BukkitPlayerEntity playerEntity) {
        le.damage(0.001, playerEntity.getPlayer());
        if (le.getHealth() > 0) {
            le.setHealth(Math.max(le.getHealth() - damage, 0));
            Bukkit.getPluginManager().callEvent(new EntityDamageByEntityEvent(playerEntity.getPlayer(), le,
                    DamageCause.CUSTOM, DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), damage));
        }
        updateName(le);
    }

    public static void updateName(LivingEntity entity) {
        if (!entity.hasMetadata("VANILLA_META")) {
            return;
        }

        VanillaEntityMeta meta = (VanillaEntityMeta) entity.getMetadata("VANILLA_META").get(0).value();

        double hp = entity.getHealth();
        double maxHp = entity.getAttribute(Attribute.MAX_HEALTH).getValue();

        String color;
        switch (meta.getRelation()) {
        case FRIENDLY -> color = "§a"; // green
        case NEUTRAL -> color = "§e"; // yellow
        case HOSTILE -> color = "§c"; // red
        default -> color = "§f";
        }

        String name = color + "[Lvl " + meta.getLevel() + "] " + entity.getType().name() + " §7" + (int) hp + "/"
                + (int) maxHp + " ❤";

        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
    }

}
