package dev.bukkit.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.entity.VanillaEntityMeta.RelationType;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.stat.BukkitStatManager;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.entity.EntityManager;
import dev.core.entity.mob.MobDefinition;
import dev.core.entity.mob.MobDefinitionRegistry;
import dev.core.entity.mob.MobEffect;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnLocation;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.loader.StatLoader;

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
        MobDefinition definition = selectMobDefinition(spawnLocation);
        if (definition == null) {
            return null; // no mob configured for this tier
        }
        return (LivingEntity) spawnDungeonMob(definition, level, spawnLocation, world);
    }

    /**
     * Weighted selection from the mob definitions registered for the spawn
     * location's tier.
     */
    private static MobDefinition selectMobDefinition(SpawnLocation spawnLocation) {
        List<MobDefinition> pool = MobDefinitionRegistry.getInstance().getForTier(spawnLocation.getTier());
        if (pool.isEmpty()) {
            return null;
        }
        // Mini-boss spots only pick `mini-boss` definitions (falling back to the
        // tier pool so a spot never spawns nothing); regular spots never pick them.
        List<MobDefinition> candidates;
        if (spawnLocation.isMiniBossSpawn()) {
            candidates = pool.stream().filter(MobDefinition::isMiniBoss).toList();
            if (candidates.isEmpty()) {
                candidates = pool;
            }
        } else {
            candidates = pool.stream().filter(definition -> !definition.isMiniBoss()).toList();
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (MobDefinition definition : candidates) {
            totalWeight += definition.getWeight();
        }
        int roll = (int) (Math.random() * totalWeight);
        int cumulative = 0;
        for (MobDefinition definition : candidates) {
            cumulative += definition.getWeight();
            if (roll < cumulative) {
                return definition;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    public static Entity spawnDungeonMob(MobDefinition definition, int level, SpawnLocation spawnLocation,
            World world) {
        if (Math.random() > spawnLocation.getSpawnChance()) {
            return null; // Nothing spawns this time
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(definition.getEntityType());
        } catch (IllegalArgumentException e) {
            System.out.println("Mob definition '" + definition.getId() + "' has unknown entity-type: "
                    + definition.getEntityType());
            return null;
        }

        VanillaEntityMeta vanillaEntityMeta = new VanillaEntityMeta(level, getRelation(entityType),
                definition.getDisplayName());

        // Create spawn location
        Location spawnLoc = new Location(world, spawnLocation.getPosition().getX() + 0.5, // Center on block
                spawnLocation.getPosition().getY(), spawnLocation.getPosition().getZ() + 0.5);

        // Spawn the entity
        Entity entity = world.spawnEntity(spawnLoc, entityType);

        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;

            // Fresh RPG StatManager per spawn (stats block scaled by level + elite bonus)
            StatManager stats = scaleStats(definition, level, spawnLocation.isEliteSpawn());
            MobRPGEntity rpgMob = new MobRPGEntity(livingEntity, definition, stats, BukkitEffectManager.getInstance(),
                    BukkitEventBus.getInstance());
            EntityManager.getInstance().registerEntity(rpgMob);

            // Vanilla MOVEMENT_SPEED derived from the (level-scaled) MOVE_SPEED stat
            // (default base 100)
            Stat moveSpeedStat = stats.getStats().get(StatType.MOVE_SPEED);
            double moveSpeedValue = moveSpeedStat == null ? 100.0
                    : moveSpeedStat.getCurrent(System.currentTimeMillis());
            setAttributeValue(livingEntity, Attribute.MOVEMENT_SPEED,
                    BukkitStatManager.computeMoveSpeed(moveSpeedValue));
            setAttributeValue(livingEntity, Attribute.KNOCKBACK_RESISTANCE,
                    Math.min(1.0, (level - 1) * 0.02 + (spawnLocation.isEliteSpawn() ? 0.05 : 0.0)));

            // Elite spawns are visually marked
            if (spawnLocation.isEliteSpawn()) {
                applyEliteEffects(livingEntity);
            }

            // Config-defined spawn effects cast on the mob's RPG facade
            applyDefinitionEffects(rpgMob, definition);

            // Main-hand weapon: vanilla material (cosmetic) or RPG item (equipped →
            // stats/abilities)
            applyMainHandItem(livingEntity, rpgMob, definition);

            // Armor slots equipped from RPG items
            applyArmor(livingEntity, rpgMob, definition);

            // Store metadata
            livingEntity.setMetadata("VANILLA_META", new FixedMetadataValue(DMain.getInstance(), vanillaEntityMeta));
            livingEntity.setMetadata("DUNGEON", new FixedMetadataValue(DMain.getInstance(), true));

            // Ensure mob doesn't despawn
            if (livingEntity instanceof Mob) {
                ((Mob) livingEntity).setRemoveWhenFarAway(false);
            }
            DamageUtils.updateName(livingEntity);

            // Boss-bar health display for mini-bosses
            if (definition.isBossBar()) {
                String title = definition.getDisplayName() != null ? definition.getDisplayName()
                        : formatMobName(entityType, level, spawnLocation.isEliteSpawn());
                MiniBossBar.track(title, livingEntity);
            }

            // Run any Java-side miniboss behavior (hybrid config + Java model).
            rpgMob.triggerSpawnBehavior(livingEntity);
            livingEntity.setNoDamageTicks(0);
        }

        return entity;
    }

    /**
     * A fresh {@link StatManager} per spawn: the definition's stats, with the
     * combat stats (HEALTH_MAX / ATTACK_DAMAGE / ARMOR) scaled by floor level and
     * elite bonus — exactly like the old per-level attribute scaling.
     */
    public static StatManager scaleStats(MobDefinition definition, int level, boolean isElite) {
        Map<StatType, Stat> stats = StatLoader.copyStats(definition.getBaseStats().getStats());
        long now = System.currentTimeMillis();

        double healthMult = (BASE_HEALTH_MULTIPLIER + ((level - 1) * HEALTH_SCALING_PER_LEVEL))
                * (1.0 + (isElite ? ELITE_HEALTH_BONUS : 0.0));
        double damageMult = (BASE_DAMAGE_MULTIPLIER + ((level - 1) * DAMAGE_SCALING_PER_LEVEL))
                * (1.0 + (isElite ? ELITE_DAMAGE_BONUS : 0.0));
        double armorMult = (1.0 + ((level - 1) * 0.05)) * (1.0 + (isElite ? 0.1 : 0.0));

        scaleStat(stats, StatType.HEALTH_MAX, healthMult, now);
        scaleStat(stats, StatType.ATTACK_DAMAGE, damageMult, now);
        scaleStat(stats, StatType.ARMOR, armorMult, now);

        // MOVE_SPEED defaults to the base custom value (100); scale per level.
        double speedMult = Math.min(MAX_SPEED_MULTIPLIER,
                BASE_SPEED_MULTIPLIER + ((level - 1) * SPEED_SCALING_PER_LEVEL))
                * (1.0 + (isElite ? ELITE_SPEED_BONUS : 0.0));
        if (!stats.containsKey(StatType.MOVE_SPEED)) {
            stats.put(StatType.MOVE_SPEED, new CombatStat("MOVE_SPEED", 100));
        }
        scaleStat(stats, StatType.MOVE_SPEED, speedMult, now);

        // The synthesized HEALTH_RESOURCE tracks HEALTH_MAX; always align it with the
        // scaled max so the mob spawns at full health no matter where the copied
        // resource drifted.
        Stat resource = stats.get(StatType.HEALTH_RESOURCE);
        Stat max = stats.get(StatType.HEALTH_MAX);
        if (resource != null && max != null) {
            double maxVal = max.getCurrent(now);
            resource.modify(maxVal - resource.getCurrent(now));
        }

        return new StatManager(stats);
    }

    private static void scaleStat(Map<StatType, Stat> stats, StatType type, double multiplier, long now) {
        Stat stat = stats.get(type);
        if (stat == null || multiplier == 1.0) {
            return;
        }
        double current = stat.getCurrent(now);
        stat.modify(current * multiplier - current);
    }

    private static void setAttributeValue(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance attributeInstance = entity.getAttribute(attribute);
        if (attributeInstance != null) {
            attributeInstance.setBaseValue(value);
        }
    }

    private static void applyEliteEffects(LivingEntity entity) {
        // Visual marker to distinguish elite mobs. Per-mob potion effects come
        // from the mob definition's `effects` list in dungeon-mobs.yml.
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
    }

    /**
     * Casts the mob definition's spawn effects (ids of {@code Effect}
     * implementations registered in {@code BukkitEffectRegistry}, e.g.
     * {@code BONE_SWING}) on the mob's RPG facade, so they tick with the rest of
     * the effect manager's active effects. Vanilla potion effects are not supported
     * here.
     */
    public static void applyDefinitionEffects(MobRPGEntity rpgMob, MobDefinition definition) {
        for (MobEffect effect : definition.getEffects()) {
            Optional<Ability> ability = AbilityRegistry.get(effect.effectId());
            if (ability.isEmpty()) {
                System.out.println("Unknown effect id '" + effect.effectId() + "' in mob definition '"
                        + definition.getId() + "'. Effect ids must be registered abilities ("
                        + "see AbilityRegistry/BukkitEffectRegistry); vanilla potion effects are not supported.");
                continue;
            }
            rpgMob.getEffectManager().cast(rpgMob, ability.get());
        }
    }

    public static void applyMainHandItem(LivingEntity entity, MobRPGEntity rpgMob, MobDefinition definition) {
        EntityEquipment equipment = entity.getEquipment();

        // RPG item weapon: cosmetic main hand + equipped on the RPG entity, so its
        // active stats apply and its abilities can be cast by the mob.
        String itemId = definition.getMainHandItemId();
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(itemId);
        if (item.isEmpty()) {
            System.out.println("Mob definition references unknown item '" + itemId + "'.");
            return;
        }
        if (equipment != null) {
            equipment.setItemInMainHand(BukkitItemStackAdapter.toItemStack(item.get()));
            equipment.setItemInMainHandDropChance(0f); // don't drop the RPG item on death
        }
        rpgMob.getEquipmentManager().equipItem(EquipmentSlot.MAIN_HAND, item.get());
    }

    public static void applyArmor(LivingEntity entity, MobRPGEntity rpgMob, MobDefinition definition) {
        for (Map.Entry<EquipmentSlot, String> entry : definition.getArmor().entrySet()) {
            Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(entry.getValue());
            if (item.isEmpty()) {
                System.out.println("Mob definition references unknown armor item '" + entry.getValue() + "' for slot "
                        + entry.getKey() + ".");
                continue;
            }
            ItemStack stack = BukkitItemStackAdapter.toItemStack(item.get());
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                switch (entry.getKey()) {
                case HEAD -> {
                    equipment.setHelmet(stack);
                    equipment.setHelmetDropChance(0f);
                }
                case CHEST -> {
                    equipment.setChestplate(stack);
                    equipment.setChestplateDropChance(0f);
                }
                case LEGS -> {
                    equipment.setLeggings(stack);
                    equipment.setLeggingsDropChance(0f);
                }
                case FEET -> {
                    equipment.setBoots(stack);
                    equipment.setBootsDropChance(0f);
                }
                default -> {
                }
                }
            }
            // Equip on the RPG entity too so the armor's stats apply.
            rpgMob.getEquipmentManager().equipItem(entry.getKey(), item.get());
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

    
    
}