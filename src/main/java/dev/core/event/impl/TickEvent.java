package dev.core.event.impl;

import dev.core.event.Event;

public class TickEvent extends Event {

    private final float tickDelta;

    public TickEvent(float tickDelta) {
        this.tickDelta = tickDelta;
    }

    public float getTickDelta() {
        return tickDelta;
    }
}
