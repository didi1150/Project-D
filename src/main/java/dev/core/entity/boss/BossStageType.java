package dev.core.entity.boss;

import dev.core.storage.config.ConfigSection;

/**
 * A config-driven stage type. Register an implementation per stage {@code type}
 * key; each instance builds a {@link BossStage} from the stage's config
 * section.
 */
public interface BossStageType {

    String getType();

    BossStage build(ConfigSection params, BossStrategyRegistry strategies);
}
