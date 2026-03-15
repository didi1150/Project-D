package dev.core.event.impl;

import dev.core.event.Event;

public class ToggleCombatEvent extends Event {

    private boolean toggled = false;

    public ToggleCombatEvent(boolean toggled) {
        this.toggled = toggled;
    }

    public boolean isToggled() {
        return toggled;
    }

}
