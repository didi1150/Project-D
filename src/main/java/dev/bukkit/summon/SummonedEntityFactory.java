package dev.bukkit.summon;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.DMain;
import dev.bukkit.ability.BukkitEffectManager;
import dev.bukkit.entity.BukkitEntityFactory;
import dev.bukkit.entity.VanillaEntityMeta;
import dev.bukkit.entity.VanillaEntityMeta.RelationType;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.stat.BukkitStatManager;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.entity.SummonRegistry;
import dev.core.entity.mob.MobDefinition;
import dev.core.entity.mob.MobDefinitionRegistry;
import dev.core.entity.mob.MobEffect;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.stat.StatManager;

/**
 * Spawns a player-owned summon from a captured {@link SoulFragment}: rebuilds
 * the mob as it was in the dungeon — same {@link MobDefinition} stats (scaled
 * to the soul's tier), same main-hand weapon + armor (so it fights with the
 * same abilities and item stats) and the same spawn effects — spawns the
 * vanilla body and registers the RPG facade. Every soul in the tome summons,
 * with no active-count limit; summons are removed individually (store-back,
 * death) or when the run ends ({@link #despawnAll}).
 */
public final class SummonedEntityFactory {

    private SummonedEntityFactory() {
    }

    /**
     * Spawns a summon for the given owner using the most recently captured soul.
     *
     * @return the spawned RPG facade, or null when the mob type could not be
     *         spawned at the owner's location.
     */
    public static SummonedMobRPGEntity spawnSummon(Player owner, SoulFragment fragment, int supportLevel) {
        if (owner == null || fragment == null) {
            return null;
        }

        SpawnTier tier = fragment.tier() != null ? fragment.tier() : SpawnTier.BASIC;
        // The captured soul remembers the exact config mob it came from; rebuild
        // the summon from that definition (stats, gear, abilities) so the summon
        // is a real copy of the dungeon mob. Legacy souls (no definition id)
        // fall back to the synthetic scaled stat block.
        MobDefinition original = resolveDefinition(fragment);
        StatManager stats = original != null
                ? BukkitEntityFactory.scaleStats(original, tier.getMinLevel(), false)
                : SummonStats.buildStats(tier, supportLevel);
        MobDefinition definition = summonDefinition(original, fragment, tier, stats);

        EntityType entityType = fragment.mobType();
        Location loc = owner.getLocation();
        if (loc.getWorld() == null) {
            return null;
        }
        org.bukkit.entity.Entity spawned = loc.getWorld().spawnEntity(loc, entityType);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            return null;
        }

        String displayName = "Summoned " + humanName(entityType);
        VanillaEntityMeta meta = new VanillaEntityMeta(SummonStats.effectiveLevel(supportLevel),
                RelationType.FRIENDLY, displayName);
        living.setMetadata("VANILLA_META", new FixedMetadataValue(DMain.getInstance(), meta));
        living.setMetadata("SUMMON_OWNER", new FixedMetadataValue(DMain.getInstance(), owner.getUniqueId().toString()));

        SummonedMobRPGEntity summon = new SummonedMobRPGEntity(owner.getUniqueId(), living, definition, stats,
                (EffectManagerInterface) BukkitEffectManager.getInstance(), BukkitEventBus.getInstance(), fragment);
        EntityManager.getInstance().registerEntity(summon);
        SummonRegistry.getInstance().register(owner.getUniqueId(), summon.getUuid());

        // Same gear as the original mob: the main-hand weapon gives the summon
        // its abilities (cast by MobRPGEntity.tryCastAbility) and item stats;
        // armor slots contribute their stats too.
        if (original != null) {
            BukkitEntityFactory.applyMainHandItem(living, summon, definition);
            BukkitEntityFactory.applyArmor(living, summon, definition);
            // Spawn effects (e.g. self-buffs/auras) mirror the mob's own; damage
            // still routes through the RPG pipeline so allies are never hit.
            BukkitEntityFactory.applyDefinitionEffects(summon, definition);
        }

        double moveSpeed = stats.getStats().get(dev.core.stat.StatType.MOVE_SPEED) == null ? 100.0
                : stats.getStats().get(dev.core.stat.StatType.MOVE_SPEED).getCurrent(System.currentTimeMillis());
        setAttribute(living, Attribute.MOVEMENT_SPEED, BukkitStatManager.computeMoveSpeed(moveSpeed));
        setAttribute(living, Attribute.KNOCKBACK_RESISTANCE, 0.5);
        living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));

        if (living instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
        }
        living.setNoDamageTicks(0);
        DamageUtils.updateName(living);
        return summon;
    }

    /**
     * The config mob the soul was captured from, or {@code null} for legacy
     * souls captured before the definition id was recorded (and for definitions
     * removed from dungeon-mobs.yml since).
     */
    private static MobDefinition resolveDefinition(SoulFragment fragment) {
        if (fragment.definitionId() == null || fragment.definitionId().isBlank()) {
            return null;
        }
        return MobDefinitionRegistry.getInstance().get(fragment.definitionId()).orElse(null);
    }

    /**
     * The definition the summon facade runs on: a copy of the captured mob's
     * definition (same weapon, armor, cast interval, ability multiplier, spawn
     * effects) with a unique id, and without miniboss/boss-bar/boss behavior —
     * the summon fights for the player with vanilla AI, not the mob's scripted
     * boss mechanics.
     */
    private static MobDefinition summonDefinition(MobDefinition original, SoulFragment fragment, SpawnTier tier,
            StatManager stats) {
        if (original == null) {
            return new MobDefinition("SUMMON_" + UUID.randomUUID(), fragment.mobType().name(), 1,
                    Set.of(tier), "Summoned " + humanName(fragment.mobType()), stats, null, 1.0, 1,
                    false, false, null, Map.of(), List.<MobEffect>of());
        }
        String displayName = "Summoned " + humanName(fragment.mobType());
        return new MobDefinition("SUMMON_" + UUID.randomUUID(), original.getEntityType(), 1,
                original.getTiers(), displayName, original.getBaseStats(), original.getMainHandItemId(),
                original.getAbilityDamageMultiplier(), original.getAbilityCastInterval(),
                false, false, null, original.getArmor(), original.getEffects());
    }

    /**
     * Despawns every summon on the server. Called when the run ends (PostGameState)
     * so a fresh run starts with no leftovers; souls captured in the dungeon are
     * kept on the tome, so a Support can still summon them into the boss fight
     * beforehand.
     */
    public static void despawnAll() {
        for (UUID summonId : SummonRegistry.getInstance().allSummonIds()) {
            EntityManager.getInstance().getEntity(summonId).ifPresent(entity -> {
                if (entity instanceof SummonedMobRPGEntity summon) {
                    summon.despawn();
                }
            });
        }
        SummonRegistry.getInstance().clearAll();
    }

    private static void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static String humanName(EntityType type) {
        String base = type.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : base.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}