package dev.bukkit.entity;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.entity.VanillaEntityMeta.RelationType;
import dev.bukkit.utils.DamageUtils;
import dev.core.game.dungeon.SpawnLocation;

public class BukkitEntityFactory {

    // Scaling constants for level progression
    private static final double BASE_HEALTH_MULTIPLIER = 1.0;
    private static final double HEALTH_SCALING_PER_LEVEL = 0.15; // 15% more health per level
    private static final double BASE_DAMAGE_MULTIPLIER = 1.0;
    private static final double DAMAGE_SCALING_PER_LEVEL = 0.10; // 10% more damage per level
    private static final double BASE_SPEED_MULTIPLIER = 1.0;
    private static final double SPEED_SCALING_PER_LEVEL = 0.02; // 2% more speed per level (capped)
    private static final double MAX_SPEED_MULTIPLIER = 1.5; // Max 150% speed

    // Elite mob bonuses (for elite spawn locations)
    private static final double ELITE_HEALTH_BONUS = 0.5; // +50% health
    private static final double ELITE_DAMAGE_BONUS = 0.25; // +25% damage
    private static final double ELITE_SPEED_BONUS = 0.1; // +10% speed

    public static LivingEntity spawnHostileVanillaDungeonMob(int level, SpawnLocation spawnLocation, World world) {
        // Select a random hostile mob type
        EntityType entityType = getRandomHostileMob();
        // Spawn the entity
        return (LivingEntity) spawnVanillaDungeonMob(entityType, level, spawnLocation, world);
    }

    private static EntityType getRandomHostileMob() {
        // Filter only HOSTILE mobs from EntityType.values()
        List<EntityType> hostileTypes = Arrays.stream(EntityType.values())
                .filter(type -> type.isAlive() && type.isSpawnable() && getRelation(type) == RelationType.HOSTILE
                        && (type.toString().contains("ZOMBIE") || type.toString().contains("SKELETON")))
                .toList();

        if (hostileTypes.isEmpty()) {
            throw new IllegalStateException("No hostile mobs available to spawn!");
        }

        return hostileTypes.get((int) (Math.random() * hostileTypes.size()));
    }

    public static Entity spawnVanillaDungeonMob(EntityType entityType, int level, SpawnLocation spawnLocation,
            World world) {
        if (Math.random() > spawnLocation.getSpawnChance()) {
            return null; // Nothing spawns this time
        }

        VanillaEntityMeta vanillaEntityMeta = new VanillaEntityMeta(level, getRelation(entityType));

        // Create spawn location
        Location spawnLoc = new Location(world, spawnLocation.getPosition().getX() + 0.5, // Center on block
                spawnLocation.getPosition().getY(), spawnLocation.getPosition().getZ() + 0.5);

        // Spawn the entity
        Entity entity = world.spawnEntity(spawnLoc, entityType);

        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;

            // Apply level scaling
            applyLevelScaling(livingEntity, level, spawnLocation.isEliteSpawn());

            // Apply special effects for elite spawns
            if (spawnLocation.isEliteSpawn()) {
                applyEliteEffects(livingEntity);
            }
            // Store metadata
            livingEntity.setMetadata("VANILLA_META", new FixedMetadataValue(DMain.getInstance(), vanillaEntityMeta));
            livingEntity.setMetadata("DUNGEON", new FixedMetadataValue(DMain.getInstance(), true));

            // Ensure mob doesn't despawn
            if (livingEntity instanceof Mob) {
                ((Mob) livingEntity).setRemoveWhenFarAway(false);
            }
            DamageUtils.updateName(livingEntity);
        }

        return entity;
    }

    private static void applyLevelScaling(LivingEntity entity, int level, boolean isElite) {
        // Scale health
        scaleAttribute(entity, Attribute.MAX_HEALTH, level, HEALTH_SCALING_PER_LEVEL,
                isElite ? ELITE_HEALTH_BONUS : 0.0);

        // Scale attack damage (if the entity has it)
        scaleAttribute(entity, Attribute.ATTACK_DAMAGE, level, DAMAGE_SCALING_PER_LEVEL,
                isElite ? ELITE_DAMAGE_BONUS : 0.0);

        // Scale movement speed (with cap)
        scaleAttributeWithCap(entity, Attribute.MOVEMENT_SPEED, level, SPEED_SCALING_PER_LEVEL, MAX_SPEED_MULTIPLIER,
                isElite ? ELITE_SPEED_BONUS : 0.0);

        // Scale armor (if the entity has it)
        scaleAttribute(entity, Attribute.ARMOR, level, 0.05, isElite ? 0.1 : 0.0); // 5% more armor per level, 10% elite
                                                                                   // bonus

        // Scale knockback resistance for higher level mobs
        scaleAttribute(entity, Attribute.KNOCKBACK_RESISTANCE, level, 0.02, isElite ? 0.05 : 0.0); // 2% per level, 5%
                                                                                                   // elite bonus

        // Heal to full health after scaling
        entity.setHealth(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    private static void scaleAttribute(LivingEntity entity, Attribute attribute, int level, double scalingPerLevel,
            double eliteBonus) {
        AttributeInstance attributeInstance = entity.getAttribute(attribute);
        if (attributeInstance != null) {
            double baseValue = attributeInstance.getBaseValue();
            double levelMultiplier = BASE_HEALTH_MULTIPLIER + ((level - 1) * scalingPerLevel);
            double eliteMultiplier = 1.0 + eliteBonus;
            double newValue = baseValue * levelMultiplier * eliteMultiplier;

            attributeInstance.setBaseValue(newValue);
        }
    }

    private static void scaleAttributeWithCap(LivingEntity entity, Attribute attribute, int level,
            double scalingPerLevel, double maxMultiplier, double eliteBonus) {
        AttributeInstance attributeInstance = entity.getAttribute(attribute);
        if (attributeInstance != null) {
            double baseValue = attributeInstance.getBaseValue();
            double levelMultiplier = Math.min(maxMultiplier, BASE_SPEED_MULTIPLIER + ((level - 1) * scalingPerLevel));
            double eliteMultiplier = 1.0 + eliteBonus;
            double newValue = baseValue * levelMultiplier * eliteMultiplier;

            attributeInstance.setBaseValue(newValue);
        }
    }

    private static void applyEliteEffects(LivingEntity entity) {
        // Add visual effects to distinguish elite mobs
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));

        // Add some beneficial effects based on mob type
        EntityType type = entity.getType();

        switch (type) {
        case ZOMBIE:
        case HUSK:
        case DROWNED:
            // Undead get regeneration
            entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
            break;

        case SKELETON:
        case STRAY:
        case WITHER_SKELETON:
            // Skeletons get strength for bow damage
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));
            break;

        case SPIDER:
        case CAVE_SPIDER:
            // Spiders get jump boost and speed
            entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, false, false));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            break;

        case CREEPER:
            // Creepers get fire resistance and speed
            entity.addPotionEffect(
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            break;

        case ENDERMAN:
            // Endermen get invisibility effect briefly
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));
            break;

        default:
            // Default elite effect - resistance
            entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            break;
        }
    }

    private static String formatMobName(EntityType entityType, int level, boolean isElite) {
        String mobName = formatEntityName(entityType.name());
        String prefix = isElite ? "§6Elite " : "§7";
        String levelText = "§f[Lvl " + level + "]";

        return prefix + mobName + " " + levelText;
    }

    private static String formatEntityName(String entityTypeName) {
        if (entityTypeName == null || entityTypeName.isEmpty()) {
            return "";
        }

        // Lowercase and replace underscores with spaces
        String base = entityTypeName.toLowerCase().replace('_', ' ');

        // Capitalize each word
        StringBuilder result = new StringBuilder();
        for (String word : base.split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }

        return result.toString().trim();
    }

    public static RelationType getRelation(EntityType type) {
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

    public static RelationType getRelation(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return RelationType.NEUTRAL; // Non-living entities are neutral
        }

        return getRelation(entity.getType());
    }

    // Utility method to spawn multiple mobs at a spawn location based on spawn
    // chance
    public static boolean trySpawnAtLocation(SpawnLocation spawnLocation, World world) {
        // Check spawn chance
        if (Math.random() > spawnLocation.getSpawnChance()) {
            return false; // Failed spawn chance roll
        }

        // Determine mob type based on spawn tier and location
        EntityType mobType = selectMobTypeForTier(spawnLocation);
        if (mobType == null) {
            return false; // No suitable mob type found
        }

        // Determine level within the spawn location's range
        int level = (int) (spawnLocation.getTier().getMinLevel()
                + Math.random() * (spawnLocation.getMaxEnemyLevel() - spawnLocation.getTier().getMinLevel() + 1));

        // Spawn the mob
        Entity spawnedEntity = spawnVanillaDungeonMob(mobType, level, spawnLocation, world);
        return spawnedEntity != null;
    }

    private static EntityType selectMobTypeForTier(SpawnLocation spawnLocation) {
        switch (spawnLocation.getTier()) {
        case BASIC:
            return getRandomMobFromArray(
                    new EntityType[] { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER });

        case ADVANCED:
            return getRandomMobFromArray(
                    new EntityType[] { EntityType.HUSK, EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.WITCH,
                            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.VINDICATOR, EntityType.PILLAGER });

        case ELITE:
            return getRandomMobFromArray(new EntityType[] { EntityType.WITHER_SKELETON, EntityType.BLAZE,
                    EntityType.ENDERMAN, EntityType.EVOKER, EntityType.RAVAGER, EntityType.PHANTOM });

        case BOSS:
            return getRandomMobFromArray(new EntityType[] { EntityType.WITHER_SKELETON, EntityType.ELDER_GUARDIAN,
                    EntityType.EVOKER, EntityType.RAVAGER // Note: Actual boss mobs like Wither/Dragon need special
                                                          // handling
            });

        default:
            return EntityType.ZOMBIE;
        }
    }

    private static EntityType getRandomMobFromArray(EntityType[] mobTypes) {
        if (mobTypes.length == 0)
            return null;
        return mobTypes[(int) (Math.random() * mobTypes.length)];
    }
}