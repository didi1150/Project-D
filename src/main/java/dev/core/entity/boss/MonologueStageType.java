package dev.core.entity.boss;

import java.util.List;

import dev.core.storage.config.ConfigSection;

public class MonologueStageType implements BossStageType {

    @Override
    public String getType() {
        return "MONOLOGUE";
    }

    @Override
    public BossStage build(ConfigSection params, BossStrategyRegistry strategies) {
        String id = params.getString("id", "");
        List<String> lines = params.getStringList("lines");
        int interval = params.getInt("tick-delay", 60);
        return new MonologueBossStage(id, lines, interval);
    }
}
