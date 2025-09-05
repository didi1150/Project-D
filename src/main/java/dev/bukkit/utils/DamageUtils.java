package dev.bukkit.utils;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import dev.bukkit.entity.VanillaEntityMeta;
import dev.bukkit.entity.VanillaEntityMeta.RelationType;

public class DamageUtils {

	public static void damageMob(LivingEntity le, double damage, Player player) {
		le.damage(0.001, player);
		if (le.getHealth() > 0) {
			le.setHealth(Math.max(le.getHealth() - damage, 0));
			Bukkit.getPluginManager().callEvent(new EntityDamageByEntityEvent(player, le, DamageCause.CUSTOM,
					DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), damage));
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

	public static RelationType getRelation(Entity entity) {
		if (!(entity instanceof LivingEntity)) {
			return RelationType.NEUTRAL; // Non-living entities are neutral
		}

		EntityType type = entity.getType();

		switch (type) {
		// FRIENDLY mobs
		case VILLAGER:
		case WANDERING_TRADER:
		case IRON_GOLEM:
		case SNOW_GOLEM:
		case ALLAY:
		case HORSE:
		case DONKEY:
		case MULE:
		case COW:
		case SHEEP:
		case PIG:
		case CHICKEN:
		case RABBIT:
		case TURTLE:
		case CAT:
		case PARROT:
		case STRIDER:
			return RelationType.FRIENDLY;

		// NEUTRAL mobs (can turn hostile if provoked)
		case WOLF:
		case BEE:
		case ENDERMAN:
		case PIGLIN:
		case PIGLIN_BRUTE: // always hostile but we keep it neutral to allow overrides
		case POLAR_BEAR:
		case LLAMA:
		case TRADER_LLAMA:
		case ZOMBIFIED_PIGLIN:
			return RelationType.NEUTRAL;

		// HOSTILE mobs
		case ZOMBIE:
		case HUSK:
		case DROWNED:
		case SKELETON:
		case STRAY:
		case WITHER_SKELETON:
		case SPIDER:
		case CAVE_SPIDER:
		case CREEPER:
		case SLIME:
		case MAGMA_CUBE:
		case PHANTOM:
		case WITCH:
		case BLAZE:
		case GHAST:
		case HOGLIN:
		case ZOGLIN:
		case EVOKER:
		case VINDICATOR:
		case PILLAGER:
		case RAVAGER:
		case SHULKER:
		case GUARDIAN:
		case ELDER_GUARDIAN:
		case VEX:
		case WITHER:
		case ENDER_DRAGON:
			return RelationType.HOSTILE;

		default:
			return RelationType.NEUTRAL;
		}
	}

}
