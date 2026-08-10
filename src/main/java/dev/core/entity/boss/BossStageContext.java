package dev.core.entity.boss;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BossStageContext {

    private final RPGBossEntity boss;
    private final BossStageManager stageManager;
    private final Map<String, Object> state;

    public BossStageContext(RPGBossEntity boss, BossStageManager stageManager) {
        this.boss = boss;
        this.stageManager = stageManager;
        this.state = new ConcurrentHashMap<>();
    }

    public RPGBossEntity getBoss() {
        return boss;
    }

    public BossStageManager getStageManager() {
        return stageManager;
    }

    public long getStageStartTime() {
        return stageManager.getStageStartTime();
    }

    public void setState(String key, Object value) {
        state.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getState(String key, Class<T> type) {
        Object value = state.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public boolean hasState(String key) {
        return state.containsKey(key);
    }
}
