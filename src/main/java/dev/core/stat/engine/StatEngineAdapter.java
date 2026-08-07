package dev.core.stat.engine;

import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.adapter.StatTypeAdapter;
import dev.core.stat.modifier.StatModifier;

/**
 * Adapter exposing a StatManager-like API while delegating attribute calculations to StatEngine.
 * This helps migrating callers from StatManager to StatEngine gradually.
 */
public final class StatEngineAdapter {

    private final StatEngine engine;
    private final StatManager manager;

    public StatEngineAdapter(StatEngine engine, StatManager manager) {
        this.engine = engine;
        this.manager = manager;
    }

    public double getCurrentValue(StatType type, long now) {
        // For resources (health/mana) rely on StatManager's current value
        String id = StatTypeAdapter.toId(type);
        double base = manager.getCurrentValue(type, now);
        return engine.computeValue(id, base);
    }

    public double getMaxValue(StatType type, long now) {
        return manager.getMaxValue(type, now);
    }

    public void setCurrentValue(StatType type, double value) {
        manager.setCurrentValue(type, value);
        engine.invalidate();
    }

    public void modifyStat(StatType type, double delta) {
        manager.modifyStat(type, delta);
        engine.invalidate();
    }

    public void addStatModifier(StatModifier mod) {
        manager.addStatModifier(mod);
        engine.invalidate();
    }

    public void removeStatModifier(StatModifier mod) {
        manager.removeStatModifier(mod);
        engine.invalidate();
    }
}
