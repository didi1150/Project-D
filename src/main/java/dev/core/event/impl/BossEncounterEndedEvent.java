package dev.core.event.impl;

import dev.core.entity.RPGEntity;

public class BossEncounterEndedEvent extends RPGEntityEvent {

    public enum Outcome {
        VICTORY, WIPE
    }

    private final Outcome outcome;

    public BossEncounterEndedEvent(RPGEntity entity, Outcome outcome) {
        super(entity);
        this.outcome = outcome;
    }

    public Outcome getOutcome() {
        return outcome;
    }
}
