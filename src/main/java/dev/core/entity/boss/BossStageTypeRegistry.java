package dev.core.entity.boss;

import java.util.Optional;

public interface BossStageTypeRegistry {

    Optional<BossStageType> resolve(String typeKey);
}
