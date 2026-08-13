package dev.core.stat;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import dev.core.stat.impl.ResourceStat;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.engine.StatEngine;

public class StatManager {

    private final Map<StatType, Stat> stats;
    private StatEngine statEngine;

    public StatManager(Map<StatType, Stat> preStats) {
        this.stats = new HashMap<StatType, Stat>();

        if (preStats == null) {
            return;
        }
        for (Entry<StatType, Stat> entry : preStats.entrySet()) {
            stats.put(entry.getKey(), entry.getValue());
        }
    }

    public void addAll(Map<StatType, Stat> stats) {
        this.stats.putAll(new HashMap<StatType, Stat>(stats));
        if (statEngine != null) statEngine.invalidate();
    }

    public void addStatModifier(StatModifier statModifier) {
        stats.get(statModifier.statType).addModifier(statModifier);
        if (statEngine != null) statEngine.invalidate();
    }

    public void removeStatModifier(StatModifier statModifier) {
        stats.get(statModifier.statType).removeModifier(statModifier);
        if (statEngine != null) statEngine.invalidate();
    }

    public void tick(long now) {
        for (Stat stat : stats.values()) {
            // removeExpired may alter modifier buckets; always invalidate engine for now
            try {
                stat.modifierBucket.removeExpired(now);
            } catch (Exception ignored) {}
            if (stat instanceof ResourceStat rs) {
                rs.tick(now);
            }
            if (statEngine != null) statEngine.invalidate();
        }
    }

    public double getCurrentValue(StatType type, long now) {
        Stat stat = stats.get(type);
        if (stat == null) {
            return 0;
        }
        return stat.getCurrent(now);
    }

    public double getMaxValue(StatType type, long now) {
        Stat stat = stats.get(type);
        if (stat == null) {
            return 0;
        }
        return stat.getMax(now);
    }

    public void setCurrentValue(StatType type, double value) {
        Stat stat = stats.get(type);
        if (stat != null) {
            stat.setCurrent(value);
        }
    }

    public void modifyStat(StatType type, double delta) {
        Stat stat = stats.get(type);
        if (stat != null) {
            stat.modify(delta);
        }
    }

    public void clearAll() {
        for (Stat stat : stats.values()) {
            stat.modifierBucket.clear();
        }

        stats.clear();
        if (statEngine != null) statEngine.invalidate();
    }

    public Map<StatType, Stat> getStats() {
        return stats;
    }

    public void setStatEngine(StatEngine engine) {
        this.statEngine = engine;
    }

}
