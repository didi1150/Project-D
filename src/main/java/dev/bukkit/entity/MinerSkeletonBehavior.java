package dev.bukkit.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import dev.bukkit.game.boss.pillar.DebugPillarService;

public final class MinerSkeletonBehavior implements MobBehavior {

    private static final long REPATH_INTERVAL_MS = 1000L;

    private final Map<UUID, Long> nextRepathAt = new ConcurrentHashMap<>();

    @Override
    public void onSpawn(MobRPGEntity mob, LivingEntity vanilla) {
        if (vanilla instanceof Mob vanillaMob) {
            vanillaMob.setAware(true);
            vanillaMob.setTarget(null);
        }
    }

    @Override
    public void onTick(MobRPGEntity mob, LivingEntity vanilla, long now) {
        if (!(vanilla instanceof Mob vanillaMob) || !vanilla.isValid() || vanilla.isDead()) {
            return;
        }

        LivingEntity core = DebugPillarService.getInstance().findNearestPillarCore(vanilla.getLocation());
        if (core == null) {
            nextRepathAt.remove(vanilla.getUniqueId());
            vanillaMob.setAware(false);
            vanillaMob.setTarget(null);
            vanillaMob.getPathfinder().stopPathfinding();
            return;
        }

        if (!vanillaMob.isAware()) {
            vanillaMob.setAware(true);
        }
        if (vanillaMob.getTarget() == null || !vanillaMob.getTarget().getUniqueId().equals(core.getUniqueId())) {
            vanillaMob.setTarget(core);
        }
        if (now >= nextRepathAt.getOrDefault(vanilla.getUniqueId(), 0L)) {
            nextRepathAt.put(vanilla.getUniqueId(), now + REPATH_INTERVAL_MS);
            vanillaMob.getPathfinder().moveTo(core.getLocation());
        }
    }

    @Override
    public void onDeath(MobRPGEntity mob, LivingEntity vanilla) {
        nextRepathAt.remove(vanilla.getUniqueId());
        if (vanilla instanceof Mob vanillaMob) {
            vanillaMob.setTarget(null);
        }
    }
}
