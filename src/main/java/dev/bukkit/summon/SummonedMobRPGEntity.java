package dev.bukkit.summon;

import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import dev.bukkit.entity.MobRPGEntity;
import dev.bukkit.utils.CombatRelation;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.EntityManager;
import dev.core.entity.SummonRegistry;
import dev.core.entity.mob.MobDefinition;
import dev.core.event.EventBusInterface;
import dev.core.stat.StatManager;

/**
 * The RPG facade of a player-owned summon. Behaves like a dungeon mob except it
 * fights FOR the owning player: it is registered in {@link SummonRegistry} (so
 * the allied-team damage and targeting rules treat it as a player), and its
 * vanilla AI is re-pointed at enemies of the player team instead of chasing
 * players.
 */
public class SummonedMobRPGEntity extends MobRPGEntity {

    private final UUID ownerId;
    private final SoulFragment soulFragment;
    private long nextTargetLookup = 0;

    /** How often the summon re-scans for an enemy target. */
    private static final long TARGET_REFRESH_MS = 200;
    /** How close an enemy must be for the summon to engage. */
    private static final double TARGET_RANGE = 12;

    public SummonedMobRPGEntity(UUID ownerId, LivingEntity vanilla, MobDefinition definition, StatManager stats,
            EffectManagerInterface effectManagerInterface, EventBusInterface eventBus, SoulFragment soulFragment) {
        super(vanilla, definition, stats, effectManagerInterface, eventBus);
        this.ownerId = ownerId;
        this.soulFragment = soulFragment;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    /** The soul this summon was spawned from; used to store it back into the tome. */
    public SoulFragment getSoulFragment() {
        return soulFragment;
    }

    @Override
    public void tick(long now) {
        // The vanilla body may be removed out from under the facade when the run
        // ends (world reset); garbage-collect the facade instead of NPE-ing.
        if (!getVanilla().isValid()) {
            if (isAlive()) {
                setAlive(false);
            }
            cleanup();
            return;
        }
        super.tick(now);
        if (!isAlive()) {
            return;
        }
        if (now >= nextTargetLookup && getVanilla() instanceof Mob mob) {
            nextTargetLookup = now + TARGET_REFRESH_MS;
            LivingEntity target = nearestEnemy(TARGET_RANGE);
            if (mob.getTarget() == null || !mob.getTarget().equals(target)) {
                mob.setTarget(target);
            }
        }
    }

    @Override
    public void onDeath() {
        super.onDeath();
        cleanup();
    }

    /** Forcefully removes the summon (e.g. to enforce the active-summon cap). */
    public void despawn() {
        if (getVanilla().isValid()) {
            getVanilla().remove();
        }
        if (isAlive()) {
            setAlive(false);
        }
        cleanup();
    }

    /** Unregister the summon from both registries after its run is over. */
    private void cleanup() {
        SummonRegistry.getInstance().unregister(getUuid());
        EntityManager.getInstance().removeEntity(getUuid());
    }

    @Override
    protected LivingEntity findAbilityTarget() {
        return nearestEnemy(TARGET_RANGE);
    }

    private LivingEntity nearestEnemy(double range) {
        LivingEntity nearest = null;
        double bestSq = range * range;
        for (Entity entity : getVanilla().getWorld().getNearbyEntities(getVanilla().getLocation(), range, range, range)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.isDead() || !living.isValid()) {
                continue;
            }
            if (!CombatRelation.isEnemy(this, entity)) {
                continue;
            }
            double distSq = entity.getLocation().distanceSquared(getVanilla().getLocation());
            if (distSq < bestSq) {
                bestSq = distSq;
                nearest = living;
            }
        }
        return nearest;
    }
}