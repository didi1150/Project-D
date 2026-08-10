package dev.core.entity.boss;

import java.util.List;
import java.util.Optional;

public class BossDefinition {
    private final String id;
    private final String displayName;
    private final String entityType;
    private final long baseHealth;
    private final List<BossStage> stages;

    public BossDefinition(String id, String displayName, String entityType, long baseHealth, List<BossStage> stages) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.baseHealth = baseHealth;
        this.stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEntityType() {
        return entityType;
    }

    public long getBaseHealth() {
        return baseHealth;
    }

    public List<BossStage> getStages() {
        return stages;
    }

    public Optional<BossStage> getStage(String stageId) {
        return stages.stream().filter(stage -> stage.getId().equals(stageId)).findFirst();
    }
}
