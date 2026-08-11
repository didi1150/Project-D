package dev.bukkit.entity.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.core.entity.boss.BossStageType;
import dev.core.entity.boss.BossStageTypeRegistry;
import dev.core.entity.boss.MonologueStageType;
import dev.core.entity.boss.ThresholdStageType;

public class BukkitBossStageTypeRegistry implements BossStageTypeRegistry {

    private final Map<String, BossStageType> types = new HashMap<>();

    public BukkitBossStageTypeRegistry() {
        register(new ThresholdStageType());
        register(new MonologueStageType());
        register(new SummonStageType());
    }

    public void register(BossStageType type) {
        types.put(type.getType().toUpperCase(), type);
    }

    @Override
    public Optional<BossStageType> resolve(String typeKey) {
        return Optional.ofNullable(types.get(typeKey.toUpperCase()));
    }
}
