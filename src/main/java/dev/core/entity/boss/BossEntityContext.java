package dev.core.entity.boss;

public interface BossEntityContext {
    void broadcast(String message);

    void spawnAdd(String entityType, int count);
}
