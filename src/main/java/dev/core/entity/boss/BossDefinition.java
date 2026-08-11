package dev.core.entity.boss;

import java.util.List;
import java.util.Optional;

import dev.core.stat.StatManager;
import lombok.Getter;

@Getter
public class BossDefinition {
    private final String id;
    private final String displayName;
    private final String entityType;
    private final int floor;
    private final String defeatStageId;
    private final StatManager baseStatManager;
    private final List<BossStage> stages;

    public BossDefinition(String id, String displayName, String entityType, int floor, String defeatStageId,
            StatManager baseStatManager, List<BossStage> stages) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.floor = floor;
        this.defeatStageId = defeatStageId;
        this.baseStatManager = baseStatManager;
        this.stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public Optional<BossStage> getStage(String stageId) {
        return stages.stream().filter(stage -> stage.getId().equals(stageId)).findFirst();
    }
}
